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
import com.plaid.client.model.LinkTokenTransactions;
import com.plaid.client.model.Products;
import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncRequestOptions;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.model.TransactionsUpdateStatus;
import com.plaid.client.request.PlaidApi;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.ApplicationBranding;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    /** Plaid allows up to 730 days on the first /transactions/sync for an Item. */
    private static final int PLAID_SYNC_MAX_DAYS = 730;
    private static final int PLAID_SYNC_MAX_PAGES = 200;
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
                .clientName(ApplicationBranding.SHORT_NAME)
                .language("en")
                .countryCodes(List.of(CountryCode.US))
                .user(user)
                .products(List.of(Products.TRANSACTIONS))
                // Request max history at Link; sync cannot extend this after the Item is initialized (Plaid API rule).
                .transactions(new LinkTokenTransactions().daysRequested(PLAID_SYNC_MAX_DAYS));

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
        final String anchorNameBefore = inst.getName();

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

        // Remove stale rows: (1) same item_id across institutions from a prior exchange, and (2) any prior link on
        // this anchor institution — uq_banking_plaid_item_owner_institution allows only one row per institution, and
        // delete-by-item_id alone misses rows left from a *different* item_id on the same institution.
        plaidItemRepository.deleteAllByOwnerUserIdAndItemId(uid, resp.getItemId());
        plaidItemRepository.deleteAllByOwnerUserIdAndInstitutionId(uid, inst.getId());

        PlaidConnectionSnapshot snap = fetchPlaidConnectionSnapshot(resp.getAccessToken());
        String plaidInstitutionalIdStored = snap.plaidInstitutionId();
        Instant now = Instant.now();

        List<AccountBase> sorted = new ArrayList<>(snap.accounts());
        sorted.sort(Comparator.comparing(BankingPlaidService::plaidAccountSortKey));

        List<Long> linkedIds = new ArrayList<>();

        if (sorted.isEmpty()) {
            persistPlaidItemRow(
                    uid,
                    inst,
                    resp.getItemId(),
                    resp.getAccessToken(),
                    plaidInstitutionalIdStored,
                    snap.summaryLines(),
                    null,
                    now);
            linkedIds.add(inst.getId());
            applyPlaidSuggestedInstitutionName(uid, inst, suggestedInstitutionName(snap.bankDisplayName(), List.of()));
        } else if (sorted.size() == 1) {
            AccountBase a = sorted.get(0);
            String acctId = plaidAccountIdOrNull(a);
            persistPlaidItemRow(
                    uid,
                    inst,
                    resp.getItemId(),
                    resp.getAccessToken(),
                    plaidInstitutionalIdStored,
                    List.of(accountSummaryLine(a)),
                    acctId,
                    now);
            linkedIds.add(inst.getId());
            applyPlaidSuggestedInstitutionName(uid, inst, proposedInstitutionNameForAccount(snap.bankDisplayName(), a));
        } else {
            for (int i = 0; i < sorted.size(); i++) {
                AccountBase acc = sorted.get(i);
                BankingInstitution targetInst;
                if (i == 0) {
                    targetInst = inst;
                } else {
                    String distinct = distinctInstitutionName(
                            uid,
                            null,
                            proposedInstitutionNameForAccount(snap.bankDisplayName(), acc));
                    BankingInstitution n = new BankingInstitution();
                    n.setOwnerUserId(uid);
                    n.setName(distinct);
                    targetInst = institutionRepository.save(n);
                }
                String acctId = plaidAccountIdOrNull(acc);
                String proposed = proposedInstitutionNameForAccount(snap.bankDisplayName(), acc);
                String resolved = distinctInstitutionName(uid, targetInst.getId(), proposed);
                if (resolved != null && !resolved.equals(targetInst.getName())) {
                    targetInst.setName(resolved);
                    institutionRepository.save(targetInst);
                }
                persistPlaidItemRow(
                        uid,
                        targetInst,
                        resp.getItemId(),
                        resp.getAccessToken(),
                        plaidInstitutionalIdStored,
                        List.of(accountSummaryLine(acc)),
                        acctId,
                        now);
                linkedIds.add(targetInst.getId());
            }
        }

        boolean renamed = !Objects.equals(anchorNameBefore, inst.getName());

        institutionRepository.flush();
        BankingInstitution refreshedAnchor =
                institutionRepository.findByIdAndOwnerUserId(inst.getId(), uid).orElse(inst);

        log.info(
                "Plaid Item linked user={} anchorInstitution={} institutions={} itemId={} anchorRenamed={}",
                uid,
                refreshedAnchor.getId(),
                linkedIds.size(),
                maskItemId(resp.getItemId()),
                renamed);

        return new BankingPlaidExchangeResponseDto(
                refreshedAnchor.getId(),
                refreshedAnchor.getName(),
                renamed,
                snap.summaryLines(),
                linkedIds);
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
        List<String> effectiveAccountFilter =
                accountFilter.stream().filter(s -> !s.isBlank()).toList();
        if (effectiveAccountFilter.isEmpty()
                && link.getPlaidAccountId() != null
                && !link.getPlaidAccountId().isBlank()) {
            effectiveAccountFilter = List.of(link.getPlaidAccountId().trim());
        }

        String subdir = plaidProps.outputSubdirectory();
        String importRoot = bankingImportProperties.importDirectory();
        String absDir = "";
        if (!importRoot.isBlank()) {
            Path root = Path.of(importRoot).toAbsolutePath().normalize();
            absDir = root.resolve(subdir).resolve(Long.toString(uid)).resolve(Long.toString(inst.getId())).toString();
        }

        List<Transaction> fetched =
                fetchAllTransactions(link.getAccessToken(), start, end, effectiveAccountFilter);

        if (fetched.isEmpty()) {
            String msg =
                    "Plaid returned no transactions between "
                            + start
                            + " and "
                            + end
                            + ". Try a wider range, wait 1–2 minutes after linking (first-time sync), or confirm this institution matches the linked account.";
            BankingImportResultDto emptyResult = new BankingImportResultDto(true, false, null, msg);
            return new BankingPlaidSyncResponseDto(0, 0, "", absDir, emptyResult);
        }

        List<PlaidOfxRow> ofxRows = new ArrayList<>();
        for (Transaction t : fetched) {
            ofxRows.add(toOfxRow(t));
        }
        byte[] qfx = BankingPlaidOfxWriter.toQfxBytes(inst.getName(), ofxRows);
        String filename =
                "plaid_" + inst.getId() + "_" + FILE_TS.format(java.time.LocalDateTime.now()) + ".qfx";

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
        LocalDate d = plaidTxnEffectiveDate(t);
        if (d == null) {
            d = LocalDate.now();
        }
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

    /**
     * Uses Plaid's recommended {@code /transactions/sync} endpoint (with date filter client-side). Legacy
     * {@code /transactions/get} often returns empty for newly linked Items or misses history without {@code days_requested}.
     */
    private List<Transaction> fetchAllTransactions(
            String accessToken, LocalDate start, LocalDate end, List<String> accountIds) {
        Map<String, Transaction> byId = new LinkedHashMap<>();
        String cursor = null;
        boolean firstPage = true;
        for (int page = 0; page < PLAID_SYNC_MAX_PAGES; page++) {
            TransactionsSyncRequest req = new TransactionsSyncRequest()
                    .clientId(plaidProps.clientId())
                    .secret(plaidProps.secret())
                    .accessToken(accessToken)
                    .count(PLAID_PAGE);
            if (cursor != null && !cursor.isBlank()) {
                req.setCursor(cursor);
            } else {
                TransactionsSyncRequestOptions opts =
                        new TransactionsSyncRequestOptions().daysRequested(plaidDaysRequestedForRangeStart(start));
                if (accountIds.size() == 1) {
                    opts.setAccountId(accountIds.get(0).trim());
                }
                req.setOptions(opts);
            }
            TransactionsSyncResponse body = execute(plaidApi().transactionsSync(req));
            if (firstPage) {
                firstPage = false;
                if (TransactionsUpdateStatus.NOT_READY.equals(body.getTransactionsUpdateStatus())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Plaid is still loading transactions for this bank. Wait 30–90 seconds after linking, then run Sync again.");
                }
            }
            ingestSyncBatch(byId, body.getAdded());
            ingestSyncBatch(byId, body.getModified());
            if (Boolean.TRUE.equals(body.getHasMore())) {
                String next = body.getNextCursor();
                if (next != null && !next.isBlank()) {
                    cursor = next;
                    continue;
                }
            }
            break;
        }

        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : byId.values()) {
            LocalDate d = plaidTxnEffectiveDate(t);
            if (d != null && !d.isBefore(start) && !d.isAfter(end)) {
                filtered.add(t);
            }
        }
        filtered.sort(Comparator.comparing(BankingPlaidService::plaidTxnEffectiveDate, Comparator.nullsLast(Comparator.naturalOrder())));
        if (filtered.isEmpty() && !byId.isEmpty()) {
            log.info(
                    "Plaid transactions/sync returned {} transaction(s) for item history but none in requested range {}..{}",
                    byId.size(),
                    start,
                    end);
        }
        return filtered;
    }

    private static void ingestSyncBatch(Map<String, Transaction> byId, List<Transaction> batch) {
        if (batch == null) {
            return;
        }
        for (Transaction t : batch) {
            String id = t.getTransactionId();
            if (id != null && !id.isBlank()) {
                byId.put(id.trim(), t);
            }
        }
    }

    private static int plaidDaysRequestedForRangeStart(LocalDate rangeStart) {
        long days = ChronoUnit.DAYS.between(rangeStart, LocalDate.now());
        if (days < 0) {
            days = 0;
        }
        return (int) Math.min(PLAID_SYNC_MAX_DAYS, Math.max(1, days + 1));
    }

    private static LocalDate plaidTxnEffectiveDate(Transaction t) {
        if (t.getDate() != null) {
            return t.getDate();
        }
        if (t.getAuthorizedDate() != null) {
            return t.getAuthorizedDate();
        }
        if (t.getDatetime() != null) {
            return t.getDatetime().toLocalDate();
        }
        return null;
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
            lines.add(accountSummaryLine(a));
        }
        return lines;
    }

    private static String accountSummaryLine(AccountBase a) {
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
        return sb.toString();
    }

    private static String proposedInstitutionNameForAccount(String bankDisplayName, AccountBase account) {
        String bank = bankDisplayName == null || bankDisplayName.isBlank() ? "Linked bank" : bankDisplayName.trim();
        return bank + " · " + accountSummaryLine(account);
    }

    private static String plaidAccountIdOrNull(AccountBase account) {
        String id = account == null ? null : account.getAccountId();
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim();
    }

    private void persistPlaidItemRow(
            long uid,
            BankingInstitution institution,
            String itemId,
            String accessToken,
            String plaidInstitutionId,
            List<String> summaryLinesForThisRow,
            String plaidAccountIdOrNull,
            Instant touchedAt) {
        BankingPlaidItem row = new BankingPlaidItem();
        row.setOwnerUserId(uid);
        row.setInstitution(institution);
        row.setItemId(itemId);
        row.setAccessToken(accessToken);
        row.setUpdatedAt(touchedAt);
        row.setCreatedAt(touchedAt);
        row.setPlaidInstitutionId(plaidInstitutionId == null ? null : plaidInstitutionId.trim());
        row.setConnectionSummary(encodeConnectionLines(summaryLinesForThisRow));
        row.setPlaidAccountId(plaidAccountIdOrNull);
        plaidItemRepository.save(row);
    }

    private void applyPlaidSuggestedInstitutionName(long uid, BankingInstitution inst, String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return;
        }
        String resolved = distinctInstitutionName(uid, inst.getId(), suggestion.trim());
        if (resolved != null && !resolved.equals(inst.getName())) {
            inst.setName(resolved);
            institutionRepository.save(inst);
        }
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

    private String distinctInstitutionName(long ownerUserId, Long exemptInstitutionId, String desired) {
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
            if (exemptInstitutionId != null && existing.get().getId().equals(exemptInstitutionId)) {
                return candidate;
            }
        }
        return trimmed + " #" + (exemptInstitutionId != null ? exemptInstitutionId : "new");
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
