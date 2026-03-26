package com.ail.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.ail.financial.CurrencyAmount;
import com.ail.insurance.policy.Policy;
import com.ail.workflow.NewBusinessReferral.AuthorityLevel;

public class NewBusinessReferralTest {

    private NewBusinessReferral sut;
    private Policy mockPolicy;

    @Before
    public void setup() {
        mockPolicy = mock(Policy.class);
        sut = new NewBusinessReferral(mockPolicy);
    }

    @Test
    public void testConstructor() {
        assertEquals(mockPolicy, sut.getPolicy());
        assertNotNull(sut.getReferralReasons());
        assertTrue(sut.getReferralReasons().isEmpty());
        assertNull(sut.getSuggestedResolution());
        assertEquals(AuthorityLevel.STANDARD, sut.getAuthorityLevelRequired());
        assertNull(sut.getCalculatedPremium());
    }

    @Test
    public void testSetAndGetPolicy() {
        Policy otherPolicy = mock(Policy.class);
        sut.setPolicy(otherPolicy);
        assertEquals(otherPolicy, sut.getPolicy());
    }

    @Test
    public void testReferralReasons() {
        sut.addReferralReason("Reason 1");
        sut.addReferralReason("Reason 2");
        assertEquals(2, sut.getReferralReasons().size());
        assertEquals("Reason 1", sut.getReferralReasons().get(0));
        assertEquals("Reason 2", sut.getReferralReasons().get(1));
    }

    @Test
    public void testSetReferralReasons() {
        List<String> reasons = new ArrayList<String>();
        reasons.add("A");
        reasons.add("B");
        sut.setReferralReasons(reasons);
        assertEquals(2, sut.getReferralReasons().size());
    }

    @Test
    public void testSuggestedResolution() {
        sut.setSuggestedResolution("Apply loading");
        assertEquals("Apply loading", sut.getSuggestedResolution());
    }

    @Test
    public void testAuthorityLevel() {
        sut.setAuthorityLevelRequired(AuthorityLevel.SENIOR);
        assertEquals(AuthorityLevel.SENIOR, sut.getAuthorityLevelRequired());

        sut.setAuthorityLevelRequired(AuthorityLevel.MANAGER);
        assertEquals(AuthorityLevel.MANAGER, sut.getAuthorityLevelRequired());

        sut.setAuthorityLevelRequired(AuthorityLevel.EXECUTIVE);
        assertEquals(AuthorityLevel.EXECUTIVE, sut.getAuthorityLevelRequired());
    }

    @Test
    public void testCalculatedPremium() {
        CurrencyAmount premium = mock(CurrencyAmount.class);
        sut.setCalculatedPremium(premium);
        assertEquals(premium, sut.getCalculatedPremium());
    }

    @Test
    public void testAuthorityLevelEnumValues() {
        AuthorityLevel[] levels = AuthorityLevel.values();
        assertEquals(4, levels.length);
        assertEquals(AuthorityLevel.STANDARD, AuthorityLevel.valueOf("STANDARD"));
        assertEquals(AuthorityLevel.SENIOR, AuthorityLevel.valueOf("SENIOR"));
        assertEquals(AuthorityLevel.MANAGER, AuthorityLevel.valueOf("MANAGER"));
        assertEquals(AuthorityLevel.EXECUTIVE, AuthorityLevel.valueOf("EXECUTIVE"));
    }
}
