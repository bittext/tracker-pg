package com.svp.tracker.member.domain;

import com.svp.tracker.auth.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "member_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MemberProfile {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Optional name for social use; not required to match legal name. */
    @Column(length = 80)
    private String nickname;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private MemberGender gender;

    @Column(length = 320)
    private String email;

    @Column(name = "phone_country_code", length = 8)
    private String phoneCountryCode;

    @Column(name = "phone_national_number", length = 32)
    private String phoneNationalNumber;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 120)
    private String city;

    @Column(name = "state_region", length = 64)
    private String stateRegion;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "validated_postal_code", length = 20)
    private String validatedPostalCode;

    @Column(name = "validated_city", length = 120)
    private String validatedCity;

    @Column(name = "validated_state_region", length = 64)
    private String validatedStateRegion;

    @Column(name = "address_use_validated_suggestion", nullable = false)
    private boolean addressUseValidatedSuggestion = false;

    @Column(name = "marketing_email_opt_in", nullable = false)
    private boolean marketingEmailOptIn = false;

    @Column(name = "marketing_sms_opt_in", nullable = false)
    private boolean marketingSmsOptIn = false;

    /** When set, member acknowledged Privacy policy (financial data & Plaid) before connecting via Link. */
    @Column(name = "plaid_financial_data_notice_accepted_at")
    private Instant plaidFinancialDataNoticeAcceptedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
