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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.CoreProxy;
import com.ail.core.Functions;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.VersionEffectiveDate;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.policy.PolicyStatus;
import com.ail.insurance.policy.Section;
import com.ail.insurance.quotation.AssessPolicyRiskService.AssessPolicyRiskCommand;
import com.ail.insurance.quotation.AssessSectionRiskService.AssessSectionRiskCommand;

@ServiceImplementation
public class AssessRiskService extends Service<AssessRiskService.AssessRiskArgument> {
    private static final long serialVersionUID = 7260448770297048139L;

    /** Configuration flag to enable/disable parallel section-level risk assessment. */
    private boolean parallelSectionAssessmentEnabled = false;

    @ServiceArgument
    public interface AssessRiskArgument extends Argument {
        /**
         * Fetch the value of the policy argument. Each of the sections in the policy is processed in turn, and appropriate
         * additions are made to their PremiumCalculateTables. The the policy's own AssessmentSheet is updated. All of the
         * is optional and under the control of the rules services defined for the product.
         * @see #setPolicyArgRet
         * @return value of policy
         */
        Policy getPolicyArgRet();

        /**
         * Set the value of the policy argument. Each of the sections in the policy is processed in turn, and appropriate
         * additions are made to their PremiumCalculateTables. The the policy's own AssessmentSheet is updated. All of the
         * is optional and under the control of the rules services defined for the product.
         * @see #getPolicyArgRet
         * @param policy New value for policy argument.
         */
        void setPolicyArgRet(Policy policy);
    }


    @ServiceCommand(defaultServiceClass=AssessRiskService.class)
    public interface AssessRiskCommand extends Command, AssessRiskArgument {}
    
    /**
     * Return the date for which rules etc should be used.
     * todo This should come from someplace in the policy.
     * @return Date to define rules to be used etc.
     */
    @Override
    public VersionEffectiveDate getVersionEffectiveDate() {
        return getCore().getVersionEffectiveDate();
    }

    /**
     * Return the product type id of the policy we're assessing the risk for as the
     * configuration namespace. The has the effect of selecting the product's configuration.
     * @return product type id
     */
    @Override
    public String getConfigurationNamespace() {
        return getCore().getConfigurationNamespace();
    }
    
    /**
     * Assess risks logic is pretty simple, all of the actual risk assessment logic is
     * handled by business rules. The steps are as follows:
     * <ol>
     * <li>Check the precondition:<li>
     *    <ol>
     *    <li>We must have been given a policy (policy!=null)</li>
     *    <li>The policy must have a status of Application (policy.status=Application)</li>
     *    <li>The policy must have a productTypeId (policy.productTypeId!=null && policy.productTypeId.length>0)</li>
     *    </ol>
     *  <li>Loop through each of the sections in the policy, and:</li>
     *   <ol>
     *   <li>Check that the section has a SectionTypeId, throw a PreconditionException if it doesn't.</li>
     *   <li>Load the service called &lt;ProductTypeID&gt;/&lt;SectionTypeID&gt;</li>
     *   <li>Create a AssessmentSheet object.</li>
     *   <li>Pass the policy, section, and assessmentSheet into the rule.</li>
     *   <li>Invoke the rule.</li>
     *   <li>Pull the assessmentSheet out of the rule and add it to the section.</li>
     *   </ol>
     *  <li>Load the service called &lt;ProductTypeID&gt;</li>
     *  <li>Create a AssessmentSheet object.</li>
     *  <li>Pass the policy, and assessmentSheet into the rule.</li>
     *  <li>Invoke the rule.</li>
     *  <li>Pull the assessmentSheet out of the rule and add it to the policy.</li>
     * </ol>
     */
    @Override
    public void invoke() throws BaseException {
        Policy policy=args.getPolicyArgRet();
        AssessmentSheet as;

        // Check that we have been given a policy
        if (policy==null) {
            throw new PreconditionException("policy==null");
        }

        // Check that the policy has a productTypeID - we'll need it to build rule names.
        if (policy.getProductTypeId()==null || policy.getProductTypeId().length()==0) {
            throw new PreconditionException("policy.productTypeId==null || policy.productTypeId==\"\"");
        }

        // Make sure the policy has a status of Application
        if (policy.getStatus()==null || !policy.getStatus().equals(PolicyStatus.APPLICATION)) {
            throw new PreconditionException("policy.status==null || policy.status!=Application");
        }

        String namespace=Functions.productNameToConfigurationNamespace(args.getPolicyArgRet().getProductTypeId());
        setCore(new CoreProxy(namespace, args.getCallersCore()).getCore());

        if (parallelSectionAssessmentEnabled && policy.getSection().size() > 1) {
            assessSectionsInParallel(policy);
        } else {
            assessSectionsSequentially(policy);
        }

        // Get the AssessmentSheet for the policy
        if (policy.getAssessmentSheet()==null) {
            // The policy doesn't have one yet, so create it.
            as=new AssessmentSheet();
        }
        else {
            // The policy has one already, so clear out our old entries and use it.
            as=policy.getAssessmentSheet();
            as.removeLinesByOrigin("AssessRisk");
        }

        // Load the rule (name=<ProductType>), and populate it with args
        AssessPolicyRiskCommand rule=getCore().newCommand("AssessPolicyRisk", AssessPolicyRiskCommand.class);
        rule.setCoreArg(getCore());
        rule.setPolicyArg(policy);
        rule.setAssessmentSheetArgRet(as);

        // invoke the rule
        rule.getAssessmentSheetArgRet().setLockingActor("AssessRisk");
        rule.invoke();
        rule.getAssessmentSheetArgRet().clearLockingActor();

        // Pull the premium calculation table out of the results and add it to the policy
        policy.setAssessmentSheet(rule.getAssessmentSheetArgRet());
    }

    /**
     * Assess sections sequentially (original behavior).
     */
    private void assessSectionsSequentially(Policy policy) throws BaseException {
        for (Section section : policy.getSection()) {
            assessSection(policy, section);
        }
    }

    /**
     * Assess sections in parallel. Each section has its own independent AssessmentSheet,
     * so there are no cross-section dependencies. Each worker thread gets its own Core
     * instance via {@code new CoreProxy(namespace, callersCore).getCore()} to avoid
     * thread-safety issues with the shared core field.
     */
    private void assessSectionsInParallel(final Policy policy) throws BaseException {
        final String namespace = Functions.productNameToConfigurationNamespace(policy.getProductTypeId());

        List<Future<BaseException>> futures = new ArrayList<Future<BaseException>>();
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(policy.getSection().size(), Runtime.getRuntime().availableProcessors()));

        for (final Section section : policy.getSection()) {
            futures.add(executor.submit(new Callable<BaseException>() {
                @Override
                public BaseException call() {
                    try {
                        Core threadCore = new CoreProxy(namespace, args.getCallersCore()).getCore();
                        assessSectionWithCore(policy, section, threadCore);
                        return null;
                    } catch (BaseException e) {
                        return e;
                    }
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PreconditionException("Parallel section assessment interrupted");
        }

        for (Future<BaseException> f : futures) {
            try {
                BaseException ex = f.get();
                if (ex != null) throw ex;
            } catch (ExecutionException e) {
                throw new PreconditionException("Parallel section assessment failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PreconditionException("Parallel section assessment interrupted: " + e.getMessage());
            }
        }
    }

    /**
     * Assess risk for a single section using the shared core instance.
     */
    private void assessSection(Policy policy, Section section) throws BaseException {
        assessSectionWithCore(policy, section, getCore());
    }

    /**
     * Assess risk for a single section using a specific Core instance.
     * This allows parallel execution by providing per-thread Core instances.
     */
    private void assessSectionWithCore(Policy policy, Section section, Core threadCore) throws BaseException {
        if (section.getSectionTypeId() == null || section.getSectionTypeId().length() == 0) {
            throw new PreconditionException("policy.section[id=" + section.getId() + "].sectionTypeId==null || policy.section[id=" + section.getId() + "].sectionTypeId==\"\"");
        }

        AssessmentSheet as;

        if (section.getAssessmentSheet() == null) {
            as = threadCore.newType(AssessmentSheet.class);
        } else {
            as = section.getAssessmentSheet();
            as.removeLinesByOrigin("AssessRisk");
        }

        String ruleName = "AssessSectionRisk/" + section.getSectionTypeId();

        AssessSectionRiskCommand rule = threadCore.newCommand(ruleName, AssessSectionRiskCommand.class);
        rule.setCoreArg(threadCore);
        rule.setPolicyArg(policy);
        rule.setSectionArg(section);
        rule.setAssessmentSheetArgRet(as);

        rule.getAssessmentSheetArgRet().setLockingActor("AssessRisk");
        rule.invoke();
        rule.getAssessmentSheetArgRet().clearLockingActor();

        section.setAssessmentSheet(rule.getAssessmentSheetArgRet());
    }

    public boolean isParallelSectionAssessmentEnabled() {
        return parallelSectionAssessmentEnabled;
    }

    public void setParallelSectionAssessmentEnabled(boolean parallelSectionAssessmentEnabled) {
        this.parallelSectionAssessmentEnabled = parallelSectionAssessmentEnabled;
    }
}


