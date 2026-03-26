/* Copyright Applied Industrial Logic Limited 2002. All rights Reserved */
/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later 
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package com.ail.insurance.quotation;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.CoreProxy;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.Marker;
import com.ail.insurance.policy.MarkerResolution;
import com.ail.insurance.policy.MarkerType;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.policy.Reference;
import com.ail.insurance.policy.ReferenceType;
import com.ail.insurance.policy.Section;

/**
 * Service that examines referral markers on a policy and attempts automatic resolution
 * based on configurable rules:
 * <ul>
 *   <li>If a ReferValueOutOfBounds referral is within a configurable tolerance (e.g., 10% over max),
 *       auto-resolve with a loading adjustment.</li>
 *   <li>If the referral reason matches a pre-approved exception list, auto-resolve.</li>
 * </ul>
 */
@ServiceImplementation
public class AutoResolveReferralService extends Service<AutoResolveReferralService.AutoResolveReferralArgument> {
    private static final long serialVersionUID = 1L;

    /** Default tolerance percentage for auto-resolving out-of-bounds referrals. */
    private static final double DEFAULT_TOLERANCE_PERCENT = 10.0;

    /** Pattern to extract value and limit from referral reason strings like "Value 1100 exceeded limit of 1000". */
    private static final Pattern EXCEEDED_LIMIT_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s+exceeded\\s+limit\\s+of\\s+(\\d+\\.?\\d*)");

    @ServiceArgument
    public interface AutoResolveReferralArgument extends Argument {
        /**
         * Fetch the policy whose referrals should be auto-resolved.
         * @return The policy.
         */
        Policy getPolicyArgRet();

        /**
         * Set the policy whose referrals should be auto-resolved.
         * @param policyArgRet The policy.
         */
        void setPolicyArgRet(Policy policyArgRet);

        /**
         * Get the tolerance percentage for out-of-bounds auto-resolution.
         * @return Tolerance as a percentage (e.g. 10.0 for 10%).
         */
        Double getTolerancePercentArg();

        /**
         * Set the tolerance percentage for out-of-bounds auto-resolution.
         * @param tolerancePercentArg Tolerance as a percentage.
         */
        void setTolerancePercentArg(Double tolerancePercentArg);

        /**
         * Get the list of pre-approved exception reasons that can be auto-resolved.
         * @return List of pre-approved reason strings.
         */
        List<String> getApprovedExceptionsArg();

        /**
         * Set the list of pre-approved exception reasons that can be auto-resolved.
         * @param approvedExceptionsArg List of pre-approved reason strings.
         */
        void setApprovedExceptionsArg(List<String> approvedExceptionsArg);

        /**
         * Get the count of referrals that were auto-resolved.
         * @return Count of auto-resolved referrals.
         */
        int getResolvedCountRet();

        /**
         * Set the count of referrals that were auto-resolved.
         * @param resolvedCountRet Count of auto-resolved referrals.
         */
        void setResolvedCountRet(int resolvedCountRet);
    }

    @ServiceCommand(defaultServiceClass = AutoResolveReferralService.class)
    public interface AutoResolveReferralCommand extends Command, AutoResolveReferralArgument {
    }

    @Override
    public void invoke() throws PreconditionException, BaseException {
        Policy policy = args.getPolicyArgRet();

        if (policy == null) {
            throw new PreconditionException("policy==null");
        }

        core = new CoreProxy(getConfigurationNamespace(), args.getCallersCore()).getCore();

        double tolerancePercent = args.getTolerancePercentArg() != null
                ? args.getTolerancePercentArg()
                : DEFAULT_TOLERANCE_PERCENT;

        List<String> approvedExceptions = args.getApprovedExceptionsArg() != null
                ? args.getApprovedExceptionsArg()
                : new ArrayList<String>();

        int resolvedCount = 0;

        // Process policy-level assessment sheet
        if (policy.getAssessmentSheet() != null) {
            resolvedCount += autoResolveSheet(policy.getAssessmentSheet(), tolerancePercent, approvedExceptions);
        }

        // Process section-level assessment sheets
        for (Section section : policy.getSection()) {
            if (section.getAssessmentSheet() != null) {
                resolvedCount += autoResolveSheet(section.getAssessmentSheet(), tolerancePercent, approvedExceptions);
            }
        }

        args.setResolvedCountRet(resolvedCount);
    }

    /**
     * Attempt to auto-resolve referral markers in a single assessment sheet.
     */
    private int autoResolveSheet(AssessmentSheet sheet, double tolerancePercent, List<String> approvedExceptions) {
        int resolved = 0;
        Hashtable<String, Marker> markers = sheet.getLinesOfType(Marker.class);

        for (Enumeration<Marker> en = markers.elements(); en.hasMoreElements();) {
            Marker marker = en.nextElement();

            // Only process unresolved REFER markers
            if (!MarkerType.REFER.equals(marker.getType())) {
                continue;
            }
            if (sheet.findResolutionByMarkerId(marker.getId()) != null) {
                continue;
            }

            String reason = marker.getReason();

            // Check if the referral reason matches a pre-approved exception
            if (matchesApprovedExceptions(reason, approvedExceptions)) {
                addAutoResolution(sheet, marker, "Auto-resolved: matches pre-approved exception list");
                resolved++;
                continue;
            }

            // Check if this is a value-out-of-bounds referral within tolerance.
            // Parse the actual value and limit from the reason string and only auto-resolve
            // if the excess percentage is within the configured tolerance.
            if (reason != null && reason.contains("exceeded limit of")) {
                if (isWithinTolerance(reason, tolerancePercent)) {
                    addAutoResolution(sheet, marker,
                            "Auto-resolved: within " + tolerancePercent + "% tolerance of limit");
                    resolved++;
                }
            }
        }

        return resolved;
    }

    /**
     * Parse the actual value and limit from a referral reason string and determine if
     * the excess is within the configured tolerance percentage.
     * <p>
     * For example, given reason "Value 1100 exceeded limit of 1000" and tolerance 10%:
     * excess = (1100 - 1000) / 1000 * 100 = 10%, which is within tolerance.
     * <p>
     * If the reason string cannot be parsed, returns false (fail-safe: do NOT auto-resolve).
     *
     * @param reason The referral reason string to parse.
     * @param tolerancePercent The maximum allowed excess as a percentage.
     * @return true if the excess is within tolerance, false otherwise.
     */
    private boolean isWithinTolerance(String reason, double tolerancePercent) {
        Matcher matcher = EXCEEDED_LIMIT_PATTERN.matcher(reason);
        if (!matcher.find()) {
            // Cannot parse the reason string - fail safe by not auto-resolving
            return false;
        }

        try {
            double actualValue = Double.parseDouble(matcher.group(1));
            double limitValue = Double.parseDouble(matcher.group(2));

            if (limitValue <= 0) {
                return false;
            }

            double excessPercent = ((actualValue - limitValue) / limitValue) * 100.0;
            return excessPercent >= 0 && excessPercent <= tolerancePercent;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean matchesApprovedExceptions(String reason, List<String> approvedExceptions) {
        if (reason == null || approvedExceptions.isEmpty()) {
            return false;
        }
        for (String exception : approvedExceptions) {
            if (reason.contains(exception)) {
                return true;
            }
        }
        return false;
    }

    private void addAutoResolution(AssessmentSheet sheet, Marker marker, String resolutionReason) {
        String resolutionId = sheet.generateLineId();
        sheet.setLockingActor("AutoResolveReferral");
        sheet.addLine(new MarkerResolution(
                resolutionId,
                resolutionReason,
                new Reference(ReferenceType.ASSESSMENT_LINE, marker.getId()),
                null));
        sheet.clearLockingActor();
    }
}
