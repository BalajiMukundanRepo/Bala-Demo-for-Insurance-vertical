package com.ail.workflow;

import java.util.ArrayList;
import java.util.List;

import com.ail.financial.CurrencyAmount;
import com.ail.insurance.policy.Policy;

/**
 * Represents a new business referral workflow item. Contains the policy being referred
 * along with metadata about the referral reasons, suggested resolution, authority level
 * required, and the calculated premium at the time of referral.
 */
public class NewBusinessReferral {
    Policy policy;

    /** List of reasons why this policy was referred. */
    private List<String> referralReasons = new ArrayList<String>();

    /** Suggested resolution for the referral, if any. */
    private String suggestedResolution;

    /** The authority level required to resolve this referral. */
    private AuthorityLevel authorityLevelRequired = AuthorityLevel.STANDARD;

    /** The calculated premium at the time of referral (before resolution). */
    private CurrencyAmount calculatedPremium;

    /**
     * Authority levels for referral resolution.
     */
    public enum AuthorityLevel {
        /** Standard underwriter can resolve. */
        STANDARD,
        /** Senior underwriter required. */
        SENIOR,
        /** Manager approval required. */
        MANAGER,
        /** Executive approval required. */
        EXECUTIVE
    }

    public NewBusinessReferral(Policy policy) {
        this.policy = policy;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public List<String> getReferralReasons() {
        return referralReasons;
    }

    public void setReferralReasons(List<String> referralReasons) {
        this.referralReasons = referralReasons;
    }

    public void addReferralReason(String reason) {
        this.referralReasons.add(reason);
    }

    public String getSuggestedResolution() {
        return suggestedResolution;
    }

    public void setSuggestedResolution(String suggestedResolution) {
        this.suggestedResolution = suggestedResolution;
    }

    public AuthorityLevel getAuthorityLevelRequired() {
        return authorityLevelRequired;
    }

    public void setAuthorityLevelRequired(AuthorityLevel authorityLevelRequired) {
        this.authorityLevelRequired = authorityLevelRequired;
    }

    public CurrencyAmount getCalculatedPremium() {
        return calculatedPremium;
    }

    public void setCalculatedPremium(CurrencyAmount calculatedPremium) {
        this.calculatedPremium = calculatedPremium;
    }
}
