package com.svp.tracker.finance.service;

import com.plaid.client.ApiClient;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountType;
import com.plaid.client.model.AccountsGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.InvestmentTransaction;
import com.plaid.client.model.InvestmentsTransactionsGetRequest;
import com.plaid.client.model.InvestmentsTransactionsGetRequestOptions;
import com.plaid.client.model.InvestmentsTransactionsGetResponse;
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
import com.plaid.client.model.Security;
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
import com.svp.tracker.finance.domain.BankingPlaidBalanceSnapshot;
import com.svp.tracker.finance.domain.BankingPlaidItem;
import com.svp.tracker.finance.dto.BankingImportResultDto;
import com.svp.tracker.finance.dto.BankingPlaidOpeningBalancesDto;
import com.svp.tracker.finance.dto.BankingPlaidOpeningBalancesDto.Account;
import com.svp.tracker.finance.dto.BankingPlaidOpeningBalancesDto.Group;
import com.svp.tracker.finance.dto.BankingPlaidOpeningBalancesDto.Month;
import com.svp.tracker.finance.dto.BankingPlaidExchangeRequestDto;
import com.svp.tracker.finance.dto.BankingPlaidExchangeResponseDto;
import com.svp.tracker.finance.dto.BankingPlaidLinkTokenResponseDto;
import com.svp.tracker.finance.dto.BankingPlaidStatusDto;
import com.svp.tracker.finance.dto.BankingPlaidSyncRequestDto;
import com.svp.tracker.finance.dto.BankingPlaidSyncResponseDto;
import com.svp.tracker.member.domain.MemberProfile;
import com.svp.tracker.member.repository.MemberProfileRepository;
import com.svp.tracker.finance.repository.BankingInstitutionRepository;
import com.svp.tracker.finance.repository.BankingPlaidBalanceSnapshotRepository;
import com.svp.tracker.finance.repository.BankingPlaidItemRepository;
import com.svp.tracker.finance.service.banking.BankingPlaidOfxWriter;
import com.svp.tracker.finance.service.banking.BankingPlaidOfxWriter.PlaidOfxRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    /** Plaid asks callers to restart /transactions/sync from the last stored cursor when data changes mid-page. */
    private static final int PLAID_SYNC_MUTATION_RETRIES = 3;
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);
    private static final Pattern ACCOUNT_MASK = Pattern.compile("…(\\d{2,4})");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final LocalDate OPENING_START = LocalDate.of(2026, 9, 1);

    private final BankingPlaidProperties plaidProps;
    private final BankingImportProperties bankingImportProperties;
    private final CurrentUserService currentUserService;
    private final BankingInstitutionRepository institutionRepository;
    private final BankingPlaidItemRepository plaidItemRepository;
    private final BankingPlaidBalanceSnapshotRepository balanceSnapshotRepository;
    private final PlatformTransactionManager transactionManager;
    private final BankingService bankingService;
    private final PlaidAccessTokenCrypto plaidAccessTokenCrypto;
    private final MemberProfileRepository memberProfileRepository;

    private volatile PlaidApi plaidApi;

    public BankingPlaidStatusDto status(long institutionId) {
        long uid = currentUserService.requireUserId();
        if (!institutionRepository.existsByIdAndOwnerUserId(institutionId, uid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found");
        }
        boolean configured = plaidProps.apiConfigured();
        LocalDate lastBalance = lastBalanceDate(uid, institutionId);
        if (!configured) {
            return new BankingPlaidStatusDto(false, false, "", List.of(), false, lastBalance);
        }
        return plaidItemRepository
                .findByOwnerUserIdAndInstitution_Id(uid, institutionId)
                .map(item -> new BankingPlaidStatusDto(
                        true, true, maskItemId(item.getItemId()), parseConnectionLines(item), item.isDailyBalanceSync(), lastBalance))
                .orElseGet(() -> new BankingPlaidStatusDto(true, false, "", List.of(), false, lastBalance));
    }

    /**
     * Turns the morning Plaid balance capture on or off for one linked institution. Enabling saves today's balances
     * immediately.
     */
    @Transactional
    public BankingPlaidStatusDto setDailyBalanceSync(long institutionId, boolean enabled) {
        long uid = currentUserService.requireUserId();
        if (!plaidProps.apiConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plaid is not configured");
        }
        BankingPlaidItem item = plaidItemRepository
                .findByOwnerUserIdAndInstitution_Id(uid, institutionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Link Plaid for this institution before turning on daily balances"));
        if (enabled) {
            writeBalances(item, LocalDate.now(CHICAGO));
        }
        item.setDailyBalanceSync(enabled);
        item.setUpdatedAt(Instant.now());
        plaidItemRepository.save(item);
        log.info("Plaid daily balance sync user={} institution={} enabled={}", uid, institutionId, enabled);
        return status(institutionId);
    }

    /** Morning job: one Plaid balance snapshot per enabled institution. */
    public void captureDailyBalances() {
        if (!plaidProps.apiConfigured()) {
            log.info("Plaid daily balance skipped; API is not configured");
            return;
        }
        List<Long> ids = plaidItemRepository.findIdsByDailyBalanceSyncTrue();
        LocalDate today = LocalDate.now(CHICAGO);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int saved = 0;
        for (Long id : ids) {
            try {
                Integer n = tx.execute(status -> {
                    BankingPlaidItem item = plaidItemRepository.findById(id).orElse(null);
                    if (item == null || !item.isDailyBalanceSync()) {
                        return 0;
                    }
                    return writeBalances(item, today);
                });
                saved += n == null ? 0 : n;
            } catch (Exception e) {
                log.warn("Plaid daily balance failed itemRow={} : {}", id, e.toString());
            }
        }
        log.info("Plaid daily balance captured accounts={} institutions={}", saved, ids.size());
    }

    @Transactional(readOnly = true)
    public BankingPlaidOpeningBalancesDto openingBalances(int year) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year is out of range");
        }
        long uid = currentUserService.requireUserId();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<BankingPlaidBalanceSnapshot> rows =
                balanceSnapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateAscInstitutionIdAsc(
                        uid, from, to);
        Map<Long, String> names = new HashMap<>();
        for (BankingPlaidBalanceSnapshot row : rows) {
            names.computeIfAbsent(row.getInstitutionId(), id -> institutionRepository
                    .findById(id)
                    .map(BankingInstitution::getName)
                    .orElse("Bank"));
        }
        Map<String, Map<String, List<Account>>> byMonth = new LinkedHashMap<>();
        for (BankingPlaidBalanceSnapshot row : rows) {
            LocalDate day = row.getSnapshotDate();
            if (day == null || day.getDayOfMonth() != 1 || day.isBefore(OPENING_START)) {
                continue;
            }
            String monthKey = String.format("%04d-%02d", day.getYear(), day.getMonthValue());
            String institute = instituteName(names.get(row.getInstitutionId()));
            byMonth.computeIfAbsent(monthKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(institute, k -> new ArrayList<>())
                    .add(new Account(row.getAccountLabel(), row.getCurrentBalance()));
        }
        List<Month> months = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<Account>>> month : byMonth.entrySet()) {
            List<Group> groups = new ArrayList<>();
            for (Map.Entry<String, List<Account>> group : month.getValue().entrySet()) {
                List<Account> accounts = new ArrayList<>(group.getValue());
                accounts.sort(Comparator.comparing(Account::amount).reversed().thenComparing(Account::label));
                BigDecimal total = BigDecimal.ZERO;
                for (Account account : accounts) {
                    total = total.add(account.amount() == null ? BigDecimal.ZERO : account.amount());
                }
                groups.add(new Group(group.getKey(), total.setScale(2, RoundingMode.HALF_UP), accounts));
            }
            groups.sort(Comparator.comparing(Group::institution));
            months.add(new Month(month.getKey(), groups));
        }
        return new BankingPlaidOpeningBalancesDto(year, months);
    }

    static String instituteName(String institutionName) {
        String name = institutionName == null ? "" : institutionName.trim();
        int cut = name.indexOf(" · ");
        String head = (cut < 0 ? name : name.substring(0, cut)).trim();
        return head.isEmpty() ? "Bank" : head;
    }

    private LocalDate lastBalanceDate(long ownerUserId, long institutionId) {
        return balanceSnapshotRepository
                .findFirstByOwnerUserIdAndInstitutionIdOrderBySnapshotDateDesc(ownerUserId, institutionId)
                .map(BankingPlaidBalanceSnapshot::getSnapshotDate)
                .orElse(null);
    }

    private int writeBalances(BankingPlaidItem item, LocalDate day) {
        String token = requirePlainAccessToken(item);
        AccountsGetRequest rq = new AccountsGetRequest()
                .clientId(plaidProps.clientId())
                .secret(plaidProps.secret())
                .accessToken(token);
        AccountsGetResponse response;
        try {
            response = execute(plaidApi().accountsGet(rq));
        } catch (ResponseStatusException e) {
            if (isItemLoginRequired(e)) {
                throw e;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid could not return balances");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid could not return balances");
        }
        List<AccountBase> accounts = response.getAccounts() == null ? List.of() : response.getAccounts();
        String only = item.getPlaidAccountId();
        int saved = 0;
        Instant now = Instant.now();
        for (AccountBase account : accounts) {
            String accountId = plaidAccountIdOrNull(account);
            if (accountId == null) {
                continue;
            }
            if (only != null && !only.isBlank() && !only.equals(accountId)) {
                continue;
            }
            BigDecimal current = money(account.getBalances() == null ? null : account.getBalances().getCurrent());
            if (current == null) {
                continue;
            }
            BankingPlaidBalanceSnapshot row = balanceSnapshotRepository
                    .findByOwnerUserIdAndInstitutionIdAndSnapshotDateAndPlaidAccountId(
                            item.getOwnerUserId(), item.getInstitution().getId(), day, accountId)
                    .orElseGet(BankingPlaidBalanceSnapshot::new);
            row.setOwnerUserId(item.getOwnerUserId());
            row.setInstitutionId(item.getInstitution().getId());
            row.setSnapshotDate(day);
            row.setPlaidAccountId(accountId);
            row.setAccountLabel(balanceLabel(account));
            row.setCurrentBalance(current);
            row.setAvailableBalance(money(account.getBalances().getAvailable()));
            String currency = account.getBalances().getIsoCurrencyCode();
            row.setIsoCurrency(currency == null || currency.isBlank() ? null : currency.trim());
            row.setCapturedAt(now);
            balanceSnapshotRepository.save(row);
            saved++;
        }
        if (saved == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid returned no balances for this institution");
        }
        return saved;
    }

    private static BigDecimal money(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String balanceLabel(AccountBase account) {
        String name = plaidFirstNonBlankName(account.getOfficialName(), account.getName());
        String kind = "";
        if (account.getSubtype() != null) {
            kind = account.getSubtype().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        } else if (account.getType() != null) {
            kind = account.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        StringBuilder sb = new StringBuilder(name);
        if (!kind.isBlank()) {
            sb.append(" (").append(kind).append(')');
        }
        if (account.getMask() != null && !account.getMask().isBlank()) {
            sb.append(" · …").append(account.getMask().trim());
        }
        String label = sb.toString();
        return label.length() > 180 ? label.substring(0, 180) : label;
    }

    public BankingPlaidLinkTokenResponseDto createLinkToken(long institutionId) {
        requirePlaidApi();
        long uid = currentUserService.requireUserId();
        requirePlaidFinancialDataPrivacyAck(uid);
        BankingInstitution inst = institutionRepository
                .findByIdAndOwnerUserId(institutionId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));

        // Omit user.phoneNumber: prefilling can trigger failed verify-SMS flows in Link (bad UX / placeholder bugs).
        LinkTokenCreateRequestUser user =
                new LinkTokenCreateRequestUser().clientUserId("tracker-" + uid + "-inst-" + inst.getId());
        Optional<BankingPlaidItem> existing =
                plaidItemRepository.findByOwnerUserIdAndInstitution_Id(uid, inst.getId());
        boolean updateMode = existing.isPresent();
        LinkTokenCreateRequest req = new LinkTokenCreateRequest()
                .clientId(plaidProps.clientId())
                .secret(plaidProps.secret())
                .clientName(ApplicationBranding.SHORT_NAME)
                .language("en")
                .countryCodes(List.of(CountryCode.US))
                .user(user);
        if (updateMode) {
            // Update mode repairs ITEM_LOGIN_REQUIRED on the existing Item. The stored access_token stays valid
            // after the user finishes Link, so the public_token must not be exchanged.
            req.accessToken(requirePlainAccessToken(existing.get()));
        } else {
            req.products(List.of(Products.TRANSACTIONS))
                    // Request max history at Link; sync cannot extend this after the Item is initialized (Plaid API rule).
                    .transactions(new LinkTokenTransactions().daysRequested(PLAID_SYNC_MAX_DAYS));
        }

        LinkTokenCreateResponse body = execute(plaidApi().linkTokenCreate(req));
        if (body.getLinkToken() == null || body.getLinkToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plaid returned an empty link_token");
        }
        String exp = body.getExpiration() != null ? body.getExpiration().toString() : "";
        return new BankingPlaidLinkTokenResponseDto(body.getLinkToken(), exp, updateMode);
    }

    @Transactional
    public BankingPlaidExchangeResponseDto exchangePublicToken(BankingPlaidExchangeRequestDto body) {
        requirePlaidApi();
        long uid = currentUserService.requireUserId();
        requirePlaidFinancialDataPrivacyAck(uid);
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
                accountFilter.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        if (effectiveAccountFilter.isEmpty()) {
            String accountId = resolveStoredAccountId(link, inst);
            if (accountId != null && !accountId.isBlank()) {
                effectiveAccountFilter = List.of(accountId);
            }
        }

        String subdir = plaidProps.outputSubdirectory();
        String importRoot = bankingImportProperties.importDirectory();
        String absDir = "";
        if (!importRoot.isBlank()) {
            Path root = Path.of(importRoot).toAbsolutePath().normalize();
            absDir = root.resolve(subdir).resolve(Long.toString(uid)).resolve(Long.toString(inst.getId())).toString();
        }

        String accessToken = requirePlainAccessToken(link);
        AccountBase investmentAccount = investmentAccount(accessToken, effectiveAccountFilter);
        List<PlaidOfxRow> ofxRows = new ArrayList<>();
        int fetchedCount;
        if (investmentAccount != null) {
            InvestmentPull investmentTransactions =
                    fetchInvestmentTransactions(accessToken, start, end, investmentAccount.getAccountId());
            fetchedCount = investmentTransactions.transactions().size();
            for (InvestmentTransaction transaction : investmentTransactions.transactions()) {
                ofxRows.add(toOfxRow(transaction, investmentTransactions.securities()));
            }
        } else {
            List<Transaction> fetched = fetchAllTransactions(accessToken, start, end, effectiveAccountFilter);
            fetchedCount = fetched.size();
            for (Transaction t : fetched) {
                ofxRows.add(toOfxRow(t));
            }
        }

        if (fetchedCount == 0) {
            String msg = investmentAccount != null
                    ? "Plaid returned no investment transactions between " + start + " and " + end + "."
                    : "Plaid returned no transactions between "
                            + start
                            + " and "
                            + end
                            + ". Try a wider range, wait 1–2 minutes after linking (first-time sync), or confirm this institution matches the linked account.";
            BankingImportResultDto emptyResult = new BankingImportResultDto(true, false, null, msg);
            return new BankingPlaidSyncResponseDto(0, 0, "", absDir, emptyResult);
        }
        byte[] qfx = BankingPlaidOfxWriter.toQfxBytes(inst.getName(), ofxRows);
        String filename =
                "plaid_" + inst.getId() + "_" + FILE_TS.format(java.time.LocalDateTime.now()) + ".qfx";

        String extraNote = "Plaid: fetched "
                + fetchedCount
                + (investmentAccount != null ? " investment transaction(s); OFX rows written=" : " transaction(s); OFX rows written=")
                + ofxRows.size()
                + "; item="
                + maskItemId(link.getItemId())
                + ".";

        BankingImportResultDto importResult = bankingService.importBytes(
                inst.getId(), filename, "application/x-ofx", qfx, subdir, extraNote);
        String relative =
                importResult.file() != null ? importResult.file().storedRelativePath() : "";

        return new BankingPlaidSyncResponseDto(
                fetchedCount, ofxRows.size(), relative, absDir, importResult);
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

    private record InvestmentPull(List<InvestmentTransaction> transactions, Map<String, Security> securities) {}

    private AccountBase investmentAccount(String accessToken, List<String> accountIds) {
        if (accountIds == null || accountIds.size() != 1) {
            return null;
        }
        AccountBase account = findAuthorizedPlaidAccount(accessToken, accountIds.get(0));
        if (account == null || !transactionsProductUnsupported(account)) {
            return null;
        }
        return account;
    }

    /**
     * Brokerage and retirement accounts are not on {@code /transactions/sync}. Plaid returns their buys, sells,
     * dividends, and cash movements from {@code /investments/transactions/get}.
     */
    private InvestmentPull fetchInvestmentTransactions(
            String accessToken, LocalDate start, LocalDate end, String accountId) {
        try {
            List<InvestmentTransaction> transactions = new ArrayList<>();
            Map<String, Security> securities = new HashMap<>();
            LocalDate chunkStart = start;
            while (!chunkStart.isAfter(end)) {
                LocalDate chunkEnd = chunkStart.plusMonths(23).minusDays(1);
                if (chunkEnd.isAfter(end)) {
                    chunkEnd = end;
                }
                int offset = 0;
                int total = Integer.MAX_VALUE;
                for (int page = 0; page < PLAID_SYNC_MAX_PAGES && offset < total; page++) {
                    InvestmentsTransactionsGetRequestOptions options = new InvestmentsTransactionsGetRequestOptions()
                            .accountIds(List.of(accountId))
                            .count(PLAID_PAGE)
                            .offset(offset);
                    InvestmentsTransactionsGetRequest request = new InvestmentsTransactionsGetRequest()
                            .clientId(plaidProps.clientId())
                            .secret(plaidProps.secret())
                            .accessToken(accessToken)
                            .startDate(chunkStart)
                            .endDate(chunkEnd)
                            .options(options);
                    InvestmentsTransactionsGetResponse body = execute(plaidApi().investmentsTransactionsGet(request));
                    if (body.getSecurities() != null) {
                        for (Security security : body.getSecurities()) {
                            if (security.getSecurityId() != null && !security.getSecurityId().isBlank()) {
                                securities.put(security.getSecurityId(), security);
                            }
                        }
                    }
                    List<InvestmentTransaction> batch = body.getInvestmentTransactions() == null
                            ? List.of()
                            : body.getInvestmentTransactions();
                    transactions.addAll(batch);
                    total = body.getTotalInvestmentTransactions() == null
                            ? offset + batch.size()
                            : body.getTotalInvestmentTransactions();
                    if (batch.isEmpty()) {
                        break;
                    }
                    offset += batch.size();
                }
                chunkStart = chunkEnd.plusDays(1);
            }
            return new InvestmentPull(transactions, securities);
        } catch (ResponseStatusException e) {
            throw investmentsSyncFailure(e);
        }
    }

    private static ResponseStatusException investmentsSyncFailure(ResponseStatusException e) {
        String reason = e.getReason() == null ? "" : e.getReason();
        if (reason.contains("PRODUCT_NOT_READY")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Plaid is still loading investment transactions for this account. Wait 30–90 seconds, then sync again.");
        }
        if (reason.contains("INVALID_PRODUCT") || reason.contains("not enabled for investments")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This Plaid account is not enabled for investment transactions. Daily balances can still be saved.");
        }
        if (reason.contains("ADDITIONAL_CONSENT_REQUIRED")
                || reason.contains("ACCESS_NOT_GRANTED")
                || reason.contains("PRODUCT_NOT_ENABLED")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Plaid has not granted investment transactions for this account. Daily balances can still be saved.");
        }
        return e;
    }

    private PlaidOfxRow toOfxRow(InvestmentTransaction transaction, Map<String, Security> securities) {
        LocalDate date = transaction.getDate() == null ? LocalDate.now() : transaction.getDate();
        double plaidAmount = transaction.getAmount() == null ? 0d : transaction.getAmount();
        BigDecimal trnAmt = BigDecimal.valueOf(plaidAmount).negate();
        Security security = transaction.getSecurityId() == null ? null : securities.get(transaction.getSecurityId());
        String ticker = security == null || security.getTickerSymbol() == null ? "" : security.getTickerSymbol().trim();
        String securityName = security == null || security.getName() == null ? "" : security.getName().trim();
        String name = firstNonBlank(transaction.getName(), securityName, ticker.isBlank() ? "Investment" : ticker);
        StringBuilder memo = new StringBuilder();
        if (transaction.getSubtype() != null) {
            memo.append(transaction.getSubtype().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        } else if (transaction.getType() != null) {
            memo.append(transaction.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        if (!ticker.isBlank()) {
            if (!memo.isEmpty()) {
                memo.append(" · ");
            }
            memo.append(ticker);
        }
        if (transaction.getQuantity() != null && transaction.getQuantity() != 0d) {
            if (!memo.isEmpty()) {
                memo.append(" · ");
            }
            memo.append("qty ")
                    .append(BigDecimal.valueOf(transaction.getQuantity()).stripTrailingZeros().toPlainString());
        }
        String fit = transaction.getInvestmentTransactionId() == null
                ? "inv-" + transaction.hashCode()
                : transaction.getInvestmentTransactionId();
        return new PlaidOfxRow(date, trnAmt, name, memo.toString().trim(), fit);
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
     *
     * <p>This app does not store a sync cursor, so each run starts at the beginning. If Plaid mutates transactions
     * while pages are in flight, the partial pages are discarded and the read starts over.
     */
    private List<Transaction> fetchAllTransactions(
            String accessToken, LocalDate start, LocalDate end, List<String> accountIds) {
        List<String> filter = accountIds == null
                ? List.of()
                : accountIds.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        if (filter.size() == 1) {
            String accountId = filter.get(0);
            AccountBase account = findAuthorizedPlaidAccount(accessToken, accountId);
            if (account == null) {
                log.info("Stored Plaid account is not on this Item; reading transactions without an account filter");
                return keepAccount(
                        fetchAllTransactionsRetryingMutation(accessToken, start, end, List.of()), accountId);
            }
        }
        try {
            return fetchAllTransactionsRetryingMutation(accessToken, start, end, filter);
        } catch (ResponseStatusException e) {
            if (!isInvalidAccountId(e) || filter.size() != 1) {
                throw e;
            }
            log.info("Plaid rejected the account filter on transactions/sync; reading all accounts and keeping this one");
            return keepAccount(fetchAllTransactionsRetryingMutation(accessToken, start, end, List.of()), filter.get(0));
        }
    }

    /** Drops other accounts on a shared Item so they are not imported into this institution. */
    private static List<Transaction> keepAccount(List<Transaction> all, String accountId) {
        List<Transaction> matched = new ArrayList<>();
        for (Transaction t : all) {
            if (accountId.equals(t.getAccountId())) {
                matched.add(t);
            }
        }
        if (!matched.isEmpty() || all.isEmpty()) {
            return matched;
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Plaid does not have permission to read transactions for this account. "
                        + "Use Reconnect bank login and leave the account selected.");
    }

    private static boolean transactionsProductUnsupported(AccountBase account) {
        AccountType type = account.getType();
        return type == AccountType.INVESTMENT || type == AccountType.BROKERAGE;
    }

    /**
     * The id saved at link time can disappear after a bank login even though the same account is still on the Item.
     * Reconnect does not exchange a new token, so sync has to adopt the account Plaid returns now.
     */
    private String resolveStoredAccountId(BankingPlaidItem link, BankingInstitution inst) {
        String accessToken = requirePlainAccessToken(link);
        List<AccountBase> accounts = listAuthorizedAccounts(accessToken);
        String saved = blankToNull(link.getPlaidAccountId());
        if (saved != null && containsAccount(accounts, saved)) {
            return saved;
        }
        AccountBase match = chooseReplacementAccount(link, inst, accounts);
        if (match == null) {
            if (accounts.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Plaid returned no accounts for this connection. Use Reconnect bank login and leave the account selected.");
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Plaid replaced the saved account, and this connection now has more than one account ("
                            + accountLabels(accounts)
                            + "). Use Reconnect bank login and leave this account selected.");
        }
        String newId = plaidAccountIdOrNull(match);
        link.setPlaidAccountId(newId);
        link.setConnectionSummary(encodeConnectionLines(List.of(accountSummaryLine(match))));
        link.setUpdatedAt(Instant.now());
        plaidItemRepository.save(link);
        log.info(
                "Rebound Plaid account user={} institution={} hadSavedId={}",
                link.getOwnerUserId(),
                inst.getId(),
                saved != null);
        return newId;
    }

    private AccountBase chooseReplacementAccount(
            BankingPlaidItem link, BankingInstitution inst, List<AccountBase> accounts) {
        if (accounts.isEmpty()) {
            return null;
        }
        Set<String> masks = masksIn(link.getConnectionSummary(), inst.getName());
        if (masks.size() == 1) {
            String mask = masks.iterator().next();
            List<AccountBase> byMask = accounts.stream()
                    .filter(account -> mask.equals(normalizeMask(account.getMask())))
                    .toList();
            if (byMask.size() == 1) {
                return byMask.get(0);
            }
        }
        if (accounts.size() == 1) {
            return accounts.get(0);
        }
        Set<String> claimed = new HashSet<>();
        for (BankingPlaidItem sibling :
                plaidItemRepository.findAllByOwnerUserIdAndItemId(link.getOwnerUserId(), link.getItemId())) {
            if (sibling.getId() != null && sibling.getId().equals(link.getId())) {
                continue;
            }
            String siblingAccountId = blankToNull(sibling.getPlaidAccountId());
            if (siblingAccountId != null && containsAccount(accounts, siblingAccountId)) {
                claimed.add(siblingAccountId);
            }
        }
        List<AccountBase> open = accounts.stream()
                .filter(account -> !claimed.contains(plaidAccountIdOrNull(account)))
                .toList();
        if (open.size() == 1) {
            return open.get(0);
        }
        String haystack = ((inst.getName() == null ? "" : inst.getName())
                        + " "
                        + (link.getConnectionSummary() == null ? "" : link.getConnectionSummary()))
                .toLowerCase(Locale.ROOT);
        List<AccountBase> named = new ArrayList<>();
        for (AccountBase account : open.isEmpty() ? accounts : open) {
            String name = plaidFirstNonBlankName(account.getOfficialName(), account.getName())
                    .toLowerCase(Locale.ROOT);
            if (name.length() >= 4 && haystack.contains(name)) {
                named.add(account);
            }
        }
        if (named.size() == 1) {
            return named.get(0);
        }
        return null;
    }

    private static boolean containsAccount(List<AccountBase> accounts, String accountId) {
        for (AccountBase account : accounts) {
            if (accountId.equals(plaidAccountIdOrNull(account))) {
                return true;
            }
        }
        return false;
    }

    private static String accountLabels(List<AccountBase> accounts) {
        List<String> labels = new ArrayList<>();
        for (AccountBase account : accounts) {
            labels.add(accountSummaryLine(account));
        }
        return String.join(", ", labels);
    }

    private static Set<String> masksIn(String... labels) {
        Set<String> masks = new LinkedHashSet<>();
        if (labels == null) {
            return masks;
        }
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            Matcher matcher = ACCOUNT_MASK.matcher(label);
            while (matcher.find()) {
                masks.add(matcher.group(1));
            }
        }
        return masks;
    }

    private static String normalizeMask(String mask) {
        if (mask == null) {
            return null;
        }
        String trimmed = mask.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AccountBase findAuthorizedPlaidAccount(String accessToken, String accountId) {
        for (AccountBase account : listAuthorizedAccounts(accessToken)) {
            if (accountId.equals(plaidAccountIdOrNull(account))) {
                return account;
            }
        }
        return null;
    }

    private List<AccountBase> listAuthorizedAccounts(String accessToken) {
        AccountsGetRequest request = new AccountsGetRequest()
                .clientId(plaidProps.clientId())
                .secret(plaidProps.secret())
                .accessToken(accessToken);
        AccountsGetResponse response = execute(plaidApi().accountsGet(request));
        return response.getAccounts() == null ? List.of() : response.getAccounts();
    }

    private List<Transaction> fetchAllTransactionsRetryingMutation(
            String accessToken, LocalDate start, LocalDate end, List<String> accountIds) {
        for (int attempt = 0; ; attempt++) {
            try {
                return fetchAllTransactionsOnce(accessToken, start, end, accountIds);
            } catch (ResponseStatusException e) {
                if (!isTransactionsSyncMutation(e) || attempt >= PLAID_SYNC_MUTATION_RETRIES) {
                    throw e;
                }
                log.info(
                        "Plaid transactions/sync changed during pagination; restarting from the beginning (attempt {}/{})",
                        attempt + 1,
                        PLAID_SYNC_MUTATION_RETRIES);
                try {
                    Thread.sleep(500L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private List<Transaction> fetchAllTransactionsOnce(
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

    private static boolean isItemLoginRequired(ResponseStatusException e) {
        String reason = e.getReason();
        return reason != null && reason.contains("ITEM_LOGIN_REQUIRED");
    }

    private static boolean isTransactionsSyncMutation(ResponseStatusException e) {
        String reason = e.getReason();
        return reason != null && reason.contains("TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION");
    }

    private static boolean isInvalidAccountId(ResponseStatusException e) {
        String reason = e.getReason();
        return reason != null && reason.contains("INVALID_ACCOUNT_ID");
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
                if (err.contains("ITEM_LOGIN_REQUIRED")) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Your bank login changed (credentials, MFA, or a required action). "
                                    + "Use Reconnect bank login to restore this Plaid connection, then sync again. "
                                    + "ITEM_LOGIN_REQUIRED");
                }
                if (err.contains("TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION")) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Plaid updated transactions while this sync was reading them. "
                                    + "Run Sync from Plaid again. TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION");
                }
                if (err.contains("INVALID_ACCOUNT_ID")) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Plaid does not have permission to read transactions for this account. "
                                    + "Use Reconnect bank login and leave the account selected. INVALID_ACCOUNT_ID");
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

    /**
     * Returns the Plaid access token for API calls. When sealing is enabled and the row still holds a legacy plaintext
     * token, persists a sealed copy then returns the plaintext for this request.
     */
    /**
     * Removes the stored Plaid Item row for this institution (access token and metadata). Does not delete imported
     * transactions or QFX files; use banking import file removal if needed.
     */
    @Transactional
    public void unlink(long institutionId) {
        long uid = currentUserService.requireUserId();
        if (!institutionRepository.existsByIdAndOwnerUserId(institutionId, uid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found");
        }
        plaidItemRepository.deleteAllByOwnerUserIdAndInstitutionId(uid, institutionId);
        log.info("Plaid Item unlinked user={} institution={}", uid, institutionId);
    }

    private void requirePlaidFinancialDataPrivacyAck(long userId) {
        MemberProfile profile = memberProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Save your member profile before connecting a bank with Plaid."));
        if (profile.getPlaidFinancialDataNoticeAcceptedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Acknowledge the Privacy policy (Financial data & Plaid) before connecting a bank. "
                            + "Open Privacy policy from the sign-in page or Finance → Banking, then record your acknowledgment.");
        }
    }

    private String requirePlainAccessToken(BankingPlaidItem link) {
        String stored = link.getAccessToken();
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!plaidAccessTokenCrypto.isEnabled()) {
            return stored;
        }
        if (stored.startsWith(PlaidAccessTokenCrypto.SEAL_PREFIX)) {
            return plaidAccessTokenCrypto.open(stored);
        }
        String sealed = plaidAccessTokenCrypto.seal(stored);
        link.setAccessToken(sealed);
        plaidItemRepository.save(link);
        return stored;
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
        row.setAccessToken(plaidAccessTokenCrypto.seal(accessToken));
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
