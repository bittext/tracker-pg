package com.svp.tracker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeedbackEmailAddressesTest {

    @Test
    void validOnly_acceptsNormalAddress() {
        assertTrue(FeedbackEmailAddresses.isValid("admin@example.com"));
        assertEquals(
                "admin@example.com",
                FeedbackEmailAddresses.validOnly(FeedbackEmailAddresses.parseCommaList("Admin@Example.com"))
                        .get(0));
    }

    @Test
    void minusExcluded_removesBlocklist() {
        var list = FeedbackEmailAddresses.parseCommaList("a@x.com,notanemail@mail.com,b@x.com");
        var excluded = FeedbackEmailAddresses.parseCommaList("notanemail@mail.com");
        assertEquals(2, FeedbackEmailAddresses.minusExcluded(list, excluded).size());
        assertFalse(FeedbackEmailAddresses.minusExcluded(list, excluded).contains("notanemail@mail.com"));
    }

    @Test
    void adminEmailListOverridesProfileSemantics() {
        var props =
                new FeedbackProperties(
                        "real.admin@example.com",
                        "fallback@example.com",
                        "notanemail@mail.com");
        assertEquals(1, props.adminEmailList().size());
        assertEquals("real.admin@example.com", props.adminEmailList().get(0));
        assertEquals("notanemail@mail.com", props.excludedEmailList().get(0));
    }
}
