package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.service.RhDailyTrackerOpenAiClient;
import com.svp.tracker.management.domain.ManagementMailAccount;
import com.svp.tracker.management.domain.ManagementMailDigest;
import com.svp.tracker.management.domain.ManagementMailDigestMessage;
import com.svp.tracker.management.domain.ManagementMailProvider;
import com.svp.tracker.management.domain.ManagementMailSlot;
import com.svp.tracker.management.dto.ManagementMailAccountDto;
import com.svp.tracker.management.dto.ManagementMailAccountWriteRequest;
import com.svp.tracker.management.dto.ManagementMailDigestDto;
import com.svp.tracker.management.dto.ManagementMailDigestMessageDto;
import com.svp.tracker.management.dto.ManagementMailTestDto;
import com.svp.tracker.management.repository.ManagementMailAccountRepository;
import com.svp.tracker.management.repository.ManagementMailDigestMessageRepository;
import com.svp.tracker.management.repository.ManagementMailDigestRepository;
import com.svp.tracker.management.service.ManagementMailInboxClient.FetchedMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementMailService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ManagementMailSlot.ZONE);

    private final CurrentUserService currentUser;
    private final ManagementMailAccountRepository accountRepository;
    private final ManagementMailDigestRepository digestRepository;
    private final ManagementMailDigestMessageRepository messageRepository;
    private final ManagementAccountsCrypto crypto;
    private final RhDailyTrackerOpenAiClient openAi;
    private final RobinhoodRhDailyTrackerProperties aiProperties;
    private final ObjectProvider<ManagementMailService> self;

    @Transactional(readOnly = true)
    public List<ManagementMailAccountDto> listAccounts() {
        return accountRepository.findByOwnerUserIdOrderByProviderAscEmailAddressAsc(currentUser.requireUserId()).stream()
                .map(this::toAccount)
                .toList();
    }

    @Transactional
    public ManagementMailAccountDto saveAccount(ManagementMailAccountWriteRequest body) {
        long owner = currentUser.requireUserId();
        ManagementMailProvider provider = provider(body.provider());
        String email = normalizeEmail(body.emailAddress());
        ManagementMailAccount account = accountRepository
                .findByOwnerUserIdOrderByProviderAscEmailAddressAsc(owner)
                .stream()
                .filter(row -> row.getProvider() == provider && email.equals(row.getEmailAddress()))
                .findFirst()
                .orElseGet(ManagementMailAccount::new);
        if (account.getId() == null) {
            account.setOwnerUserId(owner);
            account.setProvider(provider);
            account.setEmailAddress(email);
            account.setCreatedAt(Instant.now());
        }
        if (body.appPassword() != null && !body.appPassword().isBlank()) {
            account.setSecretEnc(crypto.seal(body.appPassword().trim()));
        } else if (account.getSecretEnc() == null || account.getSecretEnc().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the app password for this mailbox.");
        }
        account.setEnabled(body.enabled() == null || body.enabled());
        account.setUpdatedAt(Instant.now());
        return toAccount(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(long id) {
        ManagementMailAccount account = ownedAccount(id);
        accountRepository.delete(account);
    }

    public ManagementMailTestDto testAccount(long id) {
        ManagementMailAccount account = ownedAccount(id);
        String secret = crypto.open(account.getSecretEnc());
        Instant since = ZonedDateTime.now(ManagementMailSlot.ZONE).minusDays(1).toInstant();
        try {
            List<FetchedMessage> messages =
                    ManagementMailInboxClient.fetchInbox(account.getProvider().imapHost(), account.getEmailAddress(), secret, since);
            return new ManagementMailTestDto(true, messages.size() + " messages in the last day.");
        } catch (Exception ex) {
            log.warn("Mail test failed provider={} ", account.getProvider());
            return new ManagementMailTestDto(false, friendly(ex));
        }
    }

    @Transactional(readOnly = true)
    public List<ManagementMailDigestDto> day(LocalDate digestOn) {
        long owner = currentUser.requireUserId();
        LocalDate day = digestOn;
        if (day == null) {
            day = digestRepository.findTop60ByOwnerUserIdOrderByDigestOnDescCreatedAtDesc(owner).stream()
                    .map(ManagementMailDigest::getDigestOn)
                    .findFirst()
                    .orElse(null);
        }
        if (day == null) {
            return List.of();
        }
        LocalDate shown = day;
        return digestRepository.findByOwnerUserIdAndDigestOn(owner, shown).stream()
                .sorted(java.util.Comparator.comparingInt(row -> row.getSlot().ordinal()))
                .map(row -> toDigest(row, messageRepository.findByDigestIdOrderByReceivedAtDesc(row.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManagementMailDigestDto> listDigests() {
        long owner = currentUser.requireUserId();
        return digestRepository.findTop60ByOwnerUserIdOrderByDigestOnDescCreatedAtDesc(owner).stream()
                .map(row -> toDigest(row, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagementMailDigestDto digest(long id) {
        ManagementMailDigest row = ownedDigest(id);
        return toDigest(row, messageRepository.findByDigestIdOrderByReceivedAtDesc(row.getId()));
    }

    @Transactional
    public void deleteDigest(long id) {
        ManagementMailDigest row = ownedDigest(id);
        messageRepository.deleteByDigestId(row.getId());
        digestRepository.delete(row);
    }

    public ManagementMailDigestDto runCurrent(String slotRaw) {
        ManagementMailSlot slot = ManagementMailSlot.fromParam(slotRaw);
        return run(currentUser.requireUserId(), slot);
    }

    public void runScheduled(ManagementMailSlot slot) {
        for (Long owner : accountRepository.findEnabledOwnerIds()) {
            try {
                run(owner, slot);
            } catch (Exception ex) {
                log.warn("Mail digest failed owner={} slot={}: {}", owner, slot, ex.toString());
            }
        }
    }

    public ManagementMailDigestDto run(long owner, ManagementMailSlot slot) {
        ZonedDateTime now = ZonedDateTime.now(ManagementMailSlot.ZONE);
        LocalDate digestOn = now.toLocalDate();
        Instant since = slot.windowStart(digestOn).toInstant();
        List<ManagementMailAccount> accounts =
                accountRepository.findByOwnerUserIdAndEnabledTrueOrderByProviderAscEmailAddressAsc(owner);
        List<MailboxRead> reads = new ArrayList<>();
        for (ManagementMailAccount account : accounts) {
            reads.add(read(account, since));
        }
        String summary = summarize(slot, digestOn, reads);
        int count = reads.stream().mapToInt(read -> read.messages().size()).sum();
        boolean anyError = reads.stream().anyMatch(read -> read.error() != null);
        String status = accounts.isEmpty() ? "EMPTY" : anyError ? "PARTIAL" : "OK";
        return self.getObject().saveDigest(owner, digestOn, slot, summary, count, status, reads);
    }

    private MailboxRead read(ManagementMailAccount account, Instant since) {
        String secret = crypto.open(account.getSecretEnc());
        if (secret == null || secret.isBlank()) {
            return new MailboxRead(account, List.of(), "No app password is saved.");
        }
        try {
            List<FetchedMessage> messages = ManagementMailInboxClient.fetchInbox(
                    account.getProvider().imapHost(), account.getEmailAddress(), secret, since);
            return new MailboxRead(account, messages, null);
        } catch (Exception ex) {
            log.warn("Mail read failed provider={}", account.getProvider());
            return new MailboxRead(account, List.of(), friendly(ex));
        }
    }

    private String summarize(ManagementMailSlot slot, LocalDate digestOn, List<MailboxRead> reads) {
        String extractive = extractive(slot, digestOn, reads);
        if (reads.stream().allMatch(read -> read.messages().isEmpty()) || !aiProperties.ai().configured()) {
            return extractive;
        }
        try {
            String written = openAi.completeText(
                    "Summarize one person's mail for a single part of the day. Group by mailbox. "
                            + "Name who wrote and what they are asking. Keep newsletters to one line. "
                            + "Use only the messages given. Plain sentences, no bullet markdown headings.",
                    extractive,
                    700,
                    0.3);
            return written == null || written.isBlank() ? extractive : written.trim();
        } catch (Exception ex) {
            log.warn("Mail summary fell back to the message list: {}", ex.toString());
            return extractive;
        }
    }

    private static String extractive(ManagementMailSlot slot, LocalDate digestOn, List<MailboxRead> reads) {
        StringBuilder out = new StringBuilder();
        out.append(slot.label()).append(" mail, ").append(digestOn).append(".\n");
        if (reads.isEmpty()) {
            out.append("No mailbox is connected yet.");
            return out.toString();
        }
        for (MailboxRead read : reads) {
            out.append('\n')
                    .append(read.account().getProvider().label())
                    .append(" (")
                    .append(read.account().getEmailAddress())
                    .append("): ");
            if (read.error() != null) {
                out.append(read.error());
                continue;
            }
            if (read.messages().isEmpty()) {
                out.append("no new mail.");
                continue;
            }
            out.append(read.messages().size()).append(read.messages().size() == 1 ? " message." : " messages.");
            for (FetchedMessage message : read.messages()) {
                out.append("\n- ");
                if (message.receivedAt() != null) {
                    out.append(WHEN.format(message.receivedAt())).append(" ");
                }
                out.append(blank(message.from(), "Unknown sender"));
                if (!message.subject().isBlank()) {
                    out.append(" — ").append(message.subject());
                }
                if (!message.snippet().isBlank()) {
                    out.append(". ").append(message.snippet());
                }
            }
        }
        return out.toString().trim();
    }

    @Transactional
    public ManagementMailDigestDto saveDigest(
            long owner,
            LocalDate digestOn,
            ManagementMailSlot slot,
            String summary,
            int count,
            String status,
            List<MailboxRead> reads) {
        ManagementMailDigest existing =
                digestRepository.findByOwnerUserIdAndDigestOnAndSlot(owner, digestOn, slot).orElse(null);
        if (existing != null) {
            messageRepository.deleteByDigestId(existing.getId());
            digestRepository.delete(existing);
            digestRepository.flush();
        }
        ManagementMailDigest digest = new ManagementMailDigest();
        digest.setOwnerUserId(owner);
        digest.setDigestOn(digestOn);
        digest.setSlot(slot);
        digest.setCreatedAt(Instant.now());
        digest.setSummary(summary);
        digest.setMessageCount(count);
        digest.setStatus(status);
        digest = digestRepository.save(digest);
        List<ManagementMailDigestMessage> stored = new ArrayList<>();
        for (MailboxRead read : reads) {
            for (FetchedMessage message : read.messages()) {
                ManagementMailDigestMessage row = new ManagementMailDigestMessage();
                row.setDigestId(digest.getId());
                row.setProvider(read.account().getProvider());
                row.setMailbox(read.account().getEmailAddress());
                row.setFromLine(clip(message.from(), 500));
                row.setSubject(clip(message.subject(), 500));
                row.setReceivedAt(message.receivedAt());
                row.setSnippet(message.snippet() == null ? "" : message.snippet());
                stored.add(row);
            }
        }
        if (!stored.isEmpty()) {
            messageRepository.saveAll(stored);
        }
        return toDigest(digest, stored);
    }

    private ManagementMailAccount ownedAccount(long id) {
        ManagementMailAccount account = accountRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mailbox not found"));
        if (!account.getOwnerUserId().equals(currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mailbox not found");
        }
        return account;
    }

    private ManagementMailDigest ownedDigest(long id) {
        ManagementMailDigest digest = digestRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Digest not found"));
        if (!digest.getOwnerUserId().equals(currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Digest not found");
        }
        return digest;
    }

    private ManagementMailAccountDto toAccount(ManagementMailAccount account) {
        return new ManagementMailAccountDto(
                account.getId(),
                account.getProvider().name(),
                account.getEmailAddress(),
                account.isEnabled(),
                account.getSecretEnc() != null && !account.getSecretEnc().isBlank(),
                account.getUpdatedAt());
    }

    private ManagementMailDigestDto toDigest(ManagementMailDigest digest, List<ManagementMailDigestMessage> messages) {
        return new ManagementMailDigestDto(
                digest.getId(),
                digest.getDigestOn(),
                digest.getSlot().name(),
                digest.getSlot().label(),
                digest.getCreatedAt(),
                digest.getSummary(),
                digest.getMessageCount(),
                digest.getStatus(),
                messages.stream()
                        .map(row -> new ManagementMailDigestMessageDto(
                                row.getId() == null ? 0 : row.getId(),
                                row.getProvider().name(),
                                row.getMailbox(),
                                row.getFromLine(),
                                row.getSubject(),
                                row.getReceivedAt(),
                                row.getSnippet()))
                        .toList());
    }

    private static ManagementMailProvider provider(String raw) {
        try {
            return ManagementMailProvider.fromParam(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private static String normalizeEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the mailbox address.");
        }
        return email;
    }

    private static String friendly(Exception ex) {
        String message = ex.getMessage() == null ? "Could not read that mailbox." : ex.getMessage();
        message = message.replaceAll("\\s+", " ").trim();
        if (message.length() > 240) {
            message = message.substring(0, 240);
        }
        return message;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private record MailboxRead(ManagementMailAccount account, List<FetchedMessage> messages, String error) {}
}
