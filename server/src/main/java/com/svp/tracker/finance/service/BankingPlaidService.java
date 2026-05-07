package com.svp.tracker.finance.service;

import com.plaid.client.ApiClient;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.InstitutionsGetByIdRequest;
import com.plaid.client.model.InstitutionsGetByIdResponse;
import com.plaid.client.model.Item;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.LinkTokenCreateResponse;
import com.plaid.client.model.Products;
import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsGetRequest;
import com.plaid.client.model.TransactionsGetRequestOptions;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.BankingImportProperties;
import com.svp.tracker.config.BankingPlaidProperties;
import com.svp.tracker.finance.domain.BankingInstitution;
import com.svp.tracker.finance.domain.BankingPlaidItem;
import com.svp.tracker.finance.dto.BankingImportResultDto;
import com.svp.tracker.finance.dto.BankingPlaidExchangeRequestDto;
import com.svp.tracker.finance.dto.BankingPlaidExchangeResponseDto;
import com.svp.tracker.finance.dto.BankingPlaidLinkTokenResponseDto;
import com.svp.tracker.finance.dto.BankingPlaidStatusDto;
import com.svp.tracker.finance.dto.BankingPlaidSyncRequestDto;
import com.svp.tracker.finance.dto.BankingPlaidSyncResponseDto;
import com.svp.tracker.finance.repository.BankingInstitutionRepository;
import com.svp.tracker.finance.repository.BankingPlaidItemRepository;
import com.svp.tracker.finance.service.banking.BankingPlaidOfxWriter;
import com.svp.tracker.finance.service.banking.BankingPlaidOfxWriter.PlaidOfxRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import retrofit2.Response;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankingPlaidService {

    private static final int PLAID_PAGE = 500;
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    private final BankingPlaidProperties plaidProps;
    private final BankingImportProperties bankingImportProperties;
    private final CurrentUserService currentUserService;
    private final BankingInstitutionRepository institutionRepository;
    private final BankingPlaidItemRepository plaidItemRepository;
    private final BankingService bankingService;

    private volatile PlaidApi plaidApi;

    public BankingPlaidStatusDto status(long institutionId) {
        long uid = currentUserService.requireUserId();
        if (!institutionRepository.existsByIdAndOwnerUserId(institutionId, uid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found");
        }
        boolean configured = plaidProps.apiConfigured();
        if (!configured) {
            return new BankingPlaidStatusDto(false, false, "", List.of());
        }
        return plaidItemRepository
                .findByOwnerUserIdAndInstitution_Id(uid, institutionId)
                .map(item ->
                        new BankingPlaidStatusDto(true, true, maskItemId(item.getItemId()), parseConnectionLines(item)))
                .orElseGet(() -> new BankingPlaidStatusDto(true, false, "", List.of()));
    }

    public BankingPlaidLinkTokenResponseDto createLinkToken(long institutionId) {
        requirePlaidApi();
        long uid = currentUserService.requireUserId();
        BankingInstitution inst = institutionRepository
                .findByIdAndOwnerUserId(institutionId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));

        // Omit user.phoneNumber: prefilling can trigger failed verify-SMS flows in Link (bad UX / placeholder bugs).
        LinkTokenCreateRequestUser user =
                new LinkTokenCreateRequestUser().clientUserId("tracker-" + uid + "-inst-" + inst.getId());
        LinkTokenCreateRequest req = new LinkTokenCreateRequest()
                .clientId(plaidProps.clientId())
                .secret(plaidProps.secret())
                .clientName("Tracker PG")
                .language("en")
                .countryCodes(List.of(CountryCode.US))
                .user(user)
                .products(List.of(Products.TRANSACTIONS));

        LinkTokenCreateResponse body = execute(plaidApi().linkTokenCreate(req));
        if (body.getLinkToken() == null || body.getLinkToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid returned an empty link_token");
        }
        String exp = body.getExpiration() != null ? body.getExpiration().toString() : "";
        return new BankingPlaidLinkTokenResponseDto(body.getLinkToken(), exp);
    }

    @Transactional
    public BankingPlaidExchangeResponseDto exchangePublicToken(BankingPlaidExchangeRequestDto body) {
        requirePlaidApi();
        long uid = currentUserService.requireUserId();
        BankingInstitution inst = institutionRepository
                .findByIdAndOwnerUserId(body.institutionId(), uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));

        ItemPublicTokenExchangeRequest req = new ItemPublicTokenExchangeRequest()
                .clientId(plaidProps.clientId())
                .secret(plaidProps.secret())
                .publicToken(body.publicToken().trim());

        ItemPublicTokenExchangeResponse resp = execute(plaidApi().itemPublicTokenExchange(req));
        if (resp.getAccessToken() == null || resp.getAccessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid returned an empty access_token");
        }
        if (resp.getItemId() == null || resp.getItemId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid returned an empty item_id");
        }

        BankingPlaidItem row = plaidItemRepository
                .findByOwnerUserIdAndInstitution_Id(uid, inst.getId())
                .orElseGet(BankingPlaidItem::new);
        row.setOwnerUserId(uid);
        row.setInstitution(inst);
        row.setItemId(resp.getItemId());
        row.setAccessToken(resp.getAccessToken());
        row.setUpdatedAt(Instant.now());
        if (row.getId() == null) {
            row.setCreatedAt(Instant.now());
        }
        plaidItemRepository.save(row);

        PlaidConnectionSnapshot snap = fetchPlaidConnectionSnapshot(resp.getAccessToken());
        row.setPlaidInstitutionId(snap.plaidInstitutionId());
        row.setConnectionSummary(encodeConnectionLines(snap.summaryLines()));
        plaidItemRepository.save(row);

        boolean renamed = false;
        String suggestion = suggestedInstitutionName(snap.bankDisplayName(), snap.accounts());
        if (!suggestion.isBlank()) {
            String resolved = distinctInstitutionName(uid, inst.getId(), suggestion);
            if (resolved != null && !resolved.equals(inst.getName())) {
                inst.setName(resolved);
                institutionRepository.save(inst);
                renamed = true;
            }
        }

        log.info(
                "Plaid Item linked user={} institution={} itemId={} renamed={}",
                uid,
                inst.getId(),
                maskItemId(resp.getItemId()),
                renamed);

        return new BankingPlaidExchangeResponseDto(inst.getId(), inst.getName(), renamed, snap.summaryLines());
    }

    @Transactional
    public BankingPlaidSyncResponseDto sync(BankingPlaidSyncRequestDto req) throws IOException {
        requirePlaidApi();
        long uid = currentUserService.requireUserId();
        BankingInstitution inst = institutionRepository
                .findByIdAndOwnerUserId(req.institutionId(), uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        BankingPlaidItem link = plaidItemRepository
                .findByOwnerUserIdAndInstitution_Id(uid, inst.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No Plaid Item linked for this institution; call /plaid/exchange first."));

        LocalDate start = req.startDate();
        LocalDate end = req.endDate();
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }

        List<String> accountFilter =
                req.accountIds() == null ? List.of() : req.accountIds().stream().filter(Objects::nonNull).toList();

        List<Transaction> fetched = fetchAllTransactions(link.getAccessToken(), start, end, accountFilter);

        List<PlaidOfxRow> ofxRows = new ArrayList<>();
        for (Transaction t : fetched) {
            ofxRows.add(toOfxRow(t));
        }
        byte[] qfx = BankingPlaidOfxWriter.toQfxBytes(inst.getName(), ofxRows);
        String filename =
                "plaid_" + inst.getId() + "_" + FILE_TS.format(java.time.LocalDateTime.now()) + ".qfx";

        String subdir = plaidProps.outputSubdirectory();

        String importRoot = bankingImportProperties.importDirectory();
        String absDir = "";
        if (!importRoot.isBlank()) {
            Path root = Path.of(importRoot).toAbsolutePath().normalize();
            absDir = root.resolve(subdir).resolve(Long.toString(uid)).resolve(Long.toString(inst.getId())).toString();
        }

        String extraNote = "Plaid: fetched "
                + fetched.size()
                + " transaction(s); OFX rows written="
                + ofxRows.size()
                + "; item="
                + maskItemId(link.getItemId())
                + ".";

        BankingImportResultDto importResult = bankingService.importBytes(
                inst.getId(), filename, "application/x-ofx", qfx, subdir, extraNote);
        String relative =
                importResult.file() != null ? importResult.file().storedRelativePath() : "";

        return new BankingPlaidSyncResponseDto(
                fetched.size(), ofxRows.size(), relative, absDir, importResult);
    }

    private PlaidOfxRow toOfxRow(Transaction t) {
        LocalDate d = t.getDate() != null ? t.getDate() : LocalDate.now();
        double plaidAmt = t.getAmount() != null ? t.getAmount() : 0d;
        // Plaid: positive amount = outflow (debit). Tracker banking: positive = credit (inflow).
        BigDecimal trnAmt = BigDecimal.valueOf(plaidAmt).negate();
        String name = firstNonBlank(t.getMerchantName(), t.getName(), "Transaction");
        StringBuilder memo = new StringBuilder();
        if (Boolean.TRUE.equals(t.getPending())) {
            memo.append("pending ");
        }
        if (t.getOriginalDescription() != null && !t.getOriginalDescription().isBlank()) {
            if (!memo.isEmpty()) {
                memo.append("| ");
            }
            memo.append(t.getOriginalDescription().trim());
        } else if (t.getCategory() != null && !t.getCategory().isEmpty()) {
            memo.append(String.join(" / ", t.getCategory()));
        }
        String fit = t.getTransactionId() != null ? t.getTransactionId() : ("p-" + t.hashCode());
        return new PlaidOfxRow(d, trnAmt, name, memo.toString().trim(), fit);
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return fallback;
    }

    private List<Transaction> fetchAllTransactions(
            String accessToken, LocalDate start, LocalDate end, List<String> accountIds) {
        List<Transaction> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            TransactionsGetRequest req = new TransactionsGetRequest()
                    .clientId(plaidProps.clientId())
                    .secret(plaidProps.secret())
                    .accessToken(accessToken)
                    .startDate(start)
                    .endDate(end);
            TransactionsGetRequestOptions opts =
                    new TransactionsGetRequestOptions().count(PLAID_PAGE).offset(offset);
            if (!accountIds.isEmpty()) {
                opts.setAccountIds(accountIds);
            }
            req.setOptions(opts);
            TransactionsGetResponse body = execute(plaidApi().transactionsGet(req));
            List<Transaction> batch = body.getTransactions() == null ? List.of() : body.getTransactions();
            if (batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < PLAID_PAGE) {
                break;
            }
            offset += PLAID_PAGE;
        }
        return all;
    }

    private void requirePlaidApi() {
        if (!plaidProps.apiConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Plaid is not configured. Set tracker.finance.banking.plaid.enabled=true, client-id, secret, and environment (see application.yml / TRACKER_PLAID_*).");
        }
    }

    private PlaidApi plaidApi() {
        if (plaidApi == null) {
            synchronized (this) {
                if (plaidApi == null) {
                    HashMap<String, String> keys = new HashMap<>();
                    keys.put("clientId", plaidProps.clientId());
                    keys.put("secret", plaidProps.secret());
                    ApiClient client = new ApiClient(keys);
                    String env = plaidProps.environment();
                    if ("production".equals(env)) {
                        client.setPlaidAdapter(ApiClient.Production);
                    } else if ("development".equals(env)) {
                        client.setPlaidAdapter("https://development.plaid.com");
                    } else {
                        client.setPlaidAdapter(ApiClient.Sandbox);
                    }
                    plaidApi = client.createService(PlaidApi.class);
                }
            }
        }
        return plaidApi;
    }

    private static <T> T execute(retrofit2.Call<T> call) {
        try {
            Response<T> resp = call.execute();
            if (!resp.isSuccessful()) {
                String err = resp.errorBody() != null ? resp.errorBody().string() : resp.message();
                if (err.contains("INVALID_PHONE_NUMBER")) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Plaid rejected the phone number on this link request. Use a valid mobile in E.164 "
                                    + "format on your profile or MFA phone (e.g. +15551234567), or remove an invalid "
                                    + "stored phone and try again. Details: "
                                    + err);
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid API error: " + err);
            }
            return resp.body();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid request failed: " + e.getMessage(), e);
        }
    }

    private List<String> parseConnectionLines(BankingPlaidItem item) {
        if (item == null || item.getConnectionSummary() == null || item.getConnectionSummary().isBlank()) {
            return List.of();
        }
        return decodeConnectionLines(item.getConnectionSummary());
    }

    /**
     * Persist summary lines as a single text value without requiring a Jackson ObjectMapper bean.
     * Format: one sanitized line per row, joined by '\n'.
     */
    private static String encodeConnectionLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String t = line.replace('\n', ' ').replace('\r', ' ').trim();
            if (!t.isEmpty()) {
                cleaned.add(t);
            }
        }
        return String.join("\n", cleaned);
    }

    private static List<String> decodeConnectionLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("\\R");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private PlaidConnectionSnapshot fetchPlaidConnectionSnapshot(String accessToken) {
        try {
            AccountsGetRequest rq = new AccountsGetRequest()
                    .clientId(plaidProps.clientId())
                    .secret(plaidProps.secret())
                    .accessToken(accessToken);
            AccountsGetResponse agr = execute(plaidApi().accountsGet(rq));
            List<AccountBase> accountsRaw = agr.getAccounts() == null ? List.of() : agr.getAccounts();
            Item itemMeta = agr.getItem();
            String instId =
                    itemMeta != null && itemMeta.getInstitutionId() != null
                            ? itemMeta.getInstitutionId().trim()
                            : "";
            String bank =
                    itemMeta != null && itemMeta.getInstitutionName() != null && !itemMeta.getInstitutionName().isBlank()
                            ? itemMeta.getInstitutionName().trim()
                            : "";
            if (bank.isEmpty() && !instId.isEmpty()) {
                try {
                    InstitutionsGetByIdRequest irq = new InstitutionsGetByIdRequest()
                            .clientId(plaidProps.clientId())
                            .secret(plaidProps.secret())
                            .institutionId(instId)
                            .countryCodes(List.of(CountryCode.US));
                    InstitutionsGetByIdResponse igr = execute(plaidApi().institutionsGetById(irq));
                    if (igr.getInstitution() != null
                            && igr.getInstitution().getName() != null
                            && !igr.getInstitution().getName().isBlank()) {
                        bank = igr.getInstitution().getName().trim();
                    }
                } catch (Exception e) {
                    log.debug("Plaid institutions/get_by_id: {}", e.getMessage());
                }
            }
            if (bank.isEmpty()) {
                bank = "Linked bank";
            }
            List<String> lines = buildAccountSummaryLines(accountsRaw);
            List<AccountBase> accountsCopy = List.copyOf(accountsRaw);
            return new PlaidConnectionSnapshot(instId.isEmpty() ? null : instId, bank, accountsCopy, lines);
        } catch (Exception e) {
            log.warn("Plaid accounts/get after link failed: {}", e.toString());
            return new PlaidConnectionSnapshot(null, "Linked bank", List.of(), List.of());
        }
    }

    private List<String> buildAccountSummaryLines(List<AccountBase> accounts) {
        List<AccountBase> sorted = new ArrayList<>(accounts);
        sorted.sort(Comparator.comparing(a -> plaidAccountSortKey(a)));
        List<String> lines = new ArrayList<>();
        for (AccountBase a : sorted) {
            String nm = plaidFirstNonBlankName(a.getOfficialName(), a.getName());
            String sub = "";
            if (a.getSubtype() != null) {
                sub = a.getSubtype().name();
            } else if (a.getType() != null) {
                sub = a.getType().name();
            }
            StringBuilder sb = new StringBuilder(nm);
            if (!sub.isBlank()) {
                sb.append(" (").append(sub.replace('_', ' ')).append(')');
            }
            if (a.getMask() != null && !a.getMask().isBlank()) {
                sb.append(" · …").append(a.getMask().trim());
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    private static String plaidAccountSortKey(AccountBase a) {
        String m = a.getMask();
        String n = plaidFirstNonBlankName(a.getOfficialName(), a.getName());
        return (m == null ? "" : m) + "\0" + n;
    }

    private static String plaidFirstNonBlankName(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return "Account";
    }

    private static String suggestedInstitutionName(String bankDisplayName, List<AccountBase> accounts) {
        String bank =
                bankDisplayName == null || bankDisplayName.isBlank() ? "Linked bank" : bankDisplayName.trim();
        if (accounts.isEmpty()) {
            return bank;
        }
        if (accounts.size() == 1) {
            AccountBase a = accounts.get(0);
            if (a.getMask() != null && !a.getMask().isBlank()) {
                return bank + " · …" + a.getMask().trim();
            }
            return bank + " · " + plaidFirstNonBlankName(a.getOfficialName(), a.getName());
        }
        return bank + " · " + accounts.size() + " accounts";
    }

    private String distinctInstitutionName(long ownerUserId, long institutionId, String desired) {
        String trimmed = desired.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 240) {
            trimmed = trimmed.substring(0, 240);
        }
        for (int i = 0; i < 100; i++) {
            String candidate = i == 0 ? trimmed : trimmed + " (" + i + ")";
            Optional<BankingInstitution> existing =
                    institutionRepository.findByOwnerUserIdAndNameIgnoreCase(ownerUserId, candidate);
            if (existing.isEmpty()) {
                return candidate;
            }
            if (existing.get().getId().equals(institutionId)) {
                return candidate;
            }
        }
        return trimmed + " #" + institutionId;
    }

    private record PlaidConnectionSnapshot(
            String plaidInstitutionId,
            String bankDisplayName,
            List<AccountBase> accounts,
            List<String> summaryLines) {}

    private static String maskItemId(String itemId) {
        if (itemId == null || itemId.length() < 8) {
            return "****";
        }
        return itemId.substring(0, 4) + "…" + itemId.substring(itemId.length() - 4);
    }
}
