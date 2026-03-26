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

package com.ail.workflow;

import java.util.Enumeration;
import java.util.Hashtable;

import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.PostconditionException;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.Marker;
import com.ail.insurance.policy.MarkerType;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.policy.Section;
import com.ail.pageflow.ExecutePageActionService;
import com.ail.pageflow.PageFlowContext;
import com.liferay.portal.kernel.workflow.WorkflowHandlerRegistryUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ServiceContextFactory;
import com.liferay.portal.util.PortalUtil;

@ServiceImplementation
public class CreateNewBusinessReferralWorkflowService extends Service<ExecutePageActionService.ExecutePageActionArgument> {
    private static final long serialVersionUID = 3198893603833694389L;

    @Override
    public void invoke() throws BaseException {

        if (args.getPortletRequestArg() == null) {
            throw new PreconditionException("args.getPortletRequestArg() == null");
        }

        if (PageFlowContext.getPageFlow() == null) {
            throw new PreconditionException("PageFlowContext.getPageFlow() == null");
        }

        if (getPolicyFromPageFlowContext() == null) {
            throw new PreconditionException("getPolicyFromPageFlowContext() == null");
        }

        // Get the that is the subject of the current PageFlow
        Policy policy = getPolicyFromPageFlowContext();

        try {
            NewBusinessReferral newBusinessReferral = new NewBusinessReferral(policy);

            // Populate referral metadata from the policy's assessment sheet markers
            populateReferralMetadata(newBusinessReferral, policy);

            User user = PortalUtil.getUser(PageFlowContext.getRequest());
            Company company = PortalUtil.getCompany(PageFlowContext.getRequest());
            ServiceContext serviceContext = ServiceContextFactory.getInstance(PageFlowContext.getRequest());

            WorkflowHandlerRegistryUtil.startWorkflowInstance(company.getCompanyId(), user.getUserId(), NewBusinessReferral.class.getName(), policy.getSystemId(), newBusinessReferral, serviceContext);
        } catch (Exception e) {
            throw new PostconditionException("Workflow creation failed.", e);
        }

        args.setModelArgRet(policy);

        if (args.getModelArgRet() == null) {
            throw new PostconditionException("args.setModelArgRet(policy)");
        }
    }

    // Wrapper to static PageFlowContext method call to help testability.
    protected Policy getPolicyFromPageFlowContext() {
        return PageFlowContext.getPolicy();
    }

    // Wrapper to static PageFlowContext method call to help testability.
    protected void setPolicyToPageFlowContext(Policy policyArg) {
        PageFlowContext.setPolicy(policyArg);
    }

    // Wrapper to static PageFlowContext method call to help testability.
    protected String getProductNameFromPageFlowContext() {
        return PageFlowContext.getProductName();
    }

    /**
     * Populate referral metadata on the NewBusinessReferral from the policy's assessment sheet markers.
     * Extracts referral reasons, calculated premium, and determines the authority level required.
     */
    protected void populateReferralMetadata(NewBusinessReferral referral, Policy policy) {
        // Collect referral reasons from the policy-level assessment sheet
        collectReferralReasons(referral, policy.getAssessmentSheet());

        // Collect referral reasons from section-level assessment sheets
        for (Section section : policy.getSection()) {
            if (section.getAssessmentSheet() != null) {
                collectReferralReasons(referral, section.getAssessmentSheet());
            }
        }

        // Set calculated premium if available
        try {
            referral.setCalculatedPremium(policy.getTotalPremium());
        } catch (IllegalStateException e) {
            // Premium not yet calculated - this is acceptable for referrals
        }

        // Determine authority level based on number of referral reasons
        int reasonCount = referral.getReferralReasons().size();
        if (reasonCount > 3) {
            referral.setAuthorityLevelRequired(NewBusinessReferral.AuthorityLevel.MANAGER);
        } else if (reasonCount > 1) {
            referral.setAuthorityLevelRequired(NewBusinessReferral.AuthorityLevel.SENIOR);
        } else {
            referral.setAuthorityLevelRequired(NewBusinessReferral.AuthorityLevel.STANDARD);
        }

        // Set a suggested resolution if there's only one simple referral
        if (reasonCount == 1) {
            referral.setSuggestedResolution("Review single referral reason and apply override if within authority.");
        } else if (reasonCount > 1) {
            referral.setSuggestedResolution("Multiple referral reasons require individual review.");
        }
    }

    /**
     * Extract referral marker reasons from an assessment sheet and add them to the referral.
     */
    private void collectReferralReasons(NewBusinessReferral referral, AssessmentSheet sheet) {
        if (sheet == null) {
            return;
        }

        Hashtable<String, Marker> markers = sheet.getLinesOfType(Marker.class);
        for (Enumeration<Marker> en = markers.elements(); en.hasMoreElements();) {
            Marker marker = en.nextElement();
            if (MarkerType.REFER.equals(marker.getType())
                    && sheet.findResolutionByMarkerId(marker.getId()) == null) {
                referral.addReferralReason(marker.getReason());
            }
        }
    }
}
