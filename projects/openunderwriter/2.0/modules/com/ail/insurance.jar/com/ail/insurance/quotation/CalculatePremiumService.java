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

import static com.ail.insurance.policy.PolicyStatus.APPLICATION;
import static com.ail.insurance.policy.PolicyStatus.DECLINED;
import static com.ail.insurance.policy.PolicyStatus.QUOTATION;
import static com.ail.insurance.policy.PolicyStatus.REFERRED;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ail.annotation.Configurable;
import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.CoreProxy;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.AssessmentLine;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.quotation.AssessRiskService.AssessRiskCommand;
import com.ail.insurance.quotation.AutoResolveReferralService.AutoResolveReferralCommand;
import com.ail.insurance.quotation.CalculateBrokerageService.CalculateBrokerageCommand;
import com.ail.insurance.quotation.CalculateCommissionService.CalculateCommissionCommand;
import com.ail.insurance.quotation.CalculateManagementChargeService.CalculateManagementChargeCommand;
import com.ail.insurance.quotation.CalculateTaxService.CalculateTaxCommand;
import com.ail.insurance.quotation.RefreshAssessmentSheetsService.RefreshAssessmentSheetsCommand;

@Configurable
@ServiceImplementation
public class CalculatePremiumService extends Service<CalculatePremiumService.CalculatePremiumArgument> {
    private static final long serialVersionUID = 7959054658477631252L;

    /** Configuration flag to enable/disable parallel execution of independent calculations. */
    private boolean parallelExecutionEnabled = false;

    @ServiceArgument
    public interface CalculatePremiumArgument extends Argument {
        /**
         * Fetch the value of the policy argument. The policy for which the premium should be calculate.
         * @see #setPolicyArgRet
         * @return value of policy
         */
        Policy getPolicyArgRet();

        /**
         * Set the value of the policy argument. The policy for which the premium should be calculate.
         * @see #getPolicyArgRet
         * @param policyArgRet New value for policy argument.
         */
        void setPolicyArgRet(Policy policyArgRet);
    }

    @ServiceCommand(defaultServiceClass=CalculatePremiumService.class)
    public interface CalculatePremiumCommand extends Command, CalculatePremiumArgument {
    }

    @Override
    public void invoke() throws PreconditionException, BaseException {
        Policy policy = args.getPolicyArgRet();

        if (policy == null) {
            throw new PreconditionException("policy==null");
        }

        if (policy.getStatus() == null || !APPLICATION.equals(policy.getStatus())) {
            throw new PreconditionException("policy.status==null || policy.status!=APPLICATION");
        }
        
        // Create a proxy to work on behalf of the caller.
        core=new CoreProxy(getConfigurationNamespace(), args.getCallersCore()).getCore();
        
        AssessRiskCommand arc=core.newCommand(AssessRiskCommand.class);
        arc.setPolicyArgRet(policy);
        arc.invoke();
        policy=arc.getPolicyArgRet();
        
        if (policy.getAssessmentSheet() == null) {
            throw new PreconditionException("policy.assessmentSheet==null");
        }

        // calculate the assessment sheet so that the other calc services get to see premiums etc.
        RefreshAssessmentSheetsCommand rasc=core.newCommand(RefreshAssessmentSheetsCommand.class);
        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("CalculatePremium");
        rasc.invoke();
        policy=rasc.getPolicyArgRet();

        if (parallelExecutionEnabled) {
            policy = executeCalculationsInParallel(policy);
        } else {
            policy = executeCalculationsSequentially(policy);
        }

        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("CalculatePremium");
        rasc.invoke();
        policy=rasc.getPolicyArgRet();

        if (policy.isMarkedForDecline()) {
            policy.setStatus(DECLINED);
        }
        else if (policy.isMarkedForRefer()) {
            // Attempt automatic resolution of referrals before setting REFERRED status
            try {
                AutoResolveReferralCommand autoResolve = core.newCommand(AutoResolveReferralCommand.class);
                autoResolve.setPolicyArgRet(policy);
                autoResolve.setTolerancePercentArg(10.0);
                autoResolve.invoke();
                policy = autoResolve.getPolicyArgRet();
            } catch (Exception e) {
                // AutoResolveReferral command not configured - continue without auto-resolution
            }

            if (!policy.isMarkedForRefer()) {
                // All referrals resolved - refresh and re-evaluate status
                rasc.setPolicyArgRet(policy);
                rasc.setOriginArg("CalculatePremium-AutoResolved");
                rasc.invoke();
                policy = rasc.getPolicyArgRet();

                if (policy.isMarkedForDecline()) {
                    policy.setStatus(DECLINED);
                } else if (policy.isMarkedForRefer()) {
                    policy.setStatus(REFERRED);
                } else {
                    policy.setStatus(QUOTATION);
                }
            } else {
                // Some referrals remain unresolved
                policy.setStatus(REFERRED);
            }
        }
        else {
            policy.setStatus(QUOTATION);
        }
    }

    /**
     * Execute the four independent calculation commands sequentially (original behavior).
     */
    private Policy executeCalculationsSequentially(Policy policy) throws BaseException {
        // calc tax
        CalculateTaxCommand calcTax=core.newCommand(CalculateTaxCommand.class);
        calcTax.setPolicyArgRet(policy);
        calcTax.invoke();
        policy=calcTax.getPolicyArgRet();

        // calc commission
        CalculateCommissionCommand calcCommission=core.newCommand(CalculateCommissionCommand.class);
        calcCommission.setPolicyArgRet(policy);
        calcCommission.invoke();
        policy=calcCommission.getPolicyArgRet();

        // calc brokerage
        CalculateBrokerageCommand calcBrokerage=core.newCommand(CalculateBrokerageCommand.class);
        calcBrokerage.setPolicyArgRet(policy);
        calcBrokerage.invoke();
        policy=calcBrokerage.getPolicyArgRet();

        // calc management charge
        CalculateManagementChargeCommand calcMgmtChg=core.newCommand(CalculateManagementChargeCommand.class);
        calcMgmtChg.setPolicyArgRet(policy);
        calcMgmtChg.invoke();
        policy=calcMgmtChg.getPolicyArgRet();

        return policy;
    }

    /**
     * Execute the four independent calculation commands in parallel. Each command gets
     * its own cloned AssessmentSheet and per-thread Core instance. Results are merged
     * back into the original sheet after all commands complete.
     */
    private Policy executeCalculationsInParallel(final Policy policy) throws BaseException {
        AssessmentSheet originalSheet = policy.getAssessmentSheet();

        try {
            final AssessmentSheet taxSheet = (AssessmentSheet) originalSheet.clone();
            final AssessmentSheet commissionSheet = (AssessmentSheet) originalSheet.clone();
            final AssessmentSheet brokerageSheet = (AssessmentSheet) originalSheet.clone();
            final AssessmentSheet mgmtChargeSheet = (AssessmentSheet) originalSheet.clone();

            final String namespace = getConfigurationNamespace();

            ExecutorService executor = Executors.newFixedThreadPool(4);

            Future<AssessmentSheet> taxFuture = executor.submit(() -> {
                Core threadCore = new CoreProxy(namespace, args.getCallersCore()).getCore();
                CalculateTaxCommand calcTax = threadCore.newCommand(CalculateTaxCommand.class);
                policy.setAssessmentSheet(taxSheet);
                calcTax.setPolicyArgRet(policy);
                calcTax.invoke();
                return calcTax.getPolicyArgRet().getAssessmentSheet();
            });

            Future<AssessmentSheet> commissionFuture = executor.submit(() -> {
                Core threadCore = new CoreProxy(namespace, args.getCallersCore()).getCore();
                CalculateCommissionCommand calcCommission = threadCore.newCommand(CalculateCommissionCommand.class);
                calcCommission.setPolicyArgRet(policy);
                calcCommission.invoke();
                return calcCommission.getPolicyArgRet().getAssessmentSheet();
            });

            Future<AssessmentSheet> brokerageFuture = executor.submit(() -> {
                Core threadCore = new CoreProxy(namespace, args.getCallersCore()).getCore();
                CalculateBrokerageCommand calcBrokerage = threadCore.newCommand(CalculateBrokerageCommand.class);
                calcBrokerage.setPolicyArgRet(policy);
                calcBrokerage.invoke();
                return calcBrokerage.getPolicyArgRet().getAssessmentSheet();
            });

            Future<AssessmentSheet> mgmtChargeFuture = executor.submit(() -> {
                Core threadCore = new CoreProxy(namespace, args.getCallersCore()).getCore();
                CalculateManagementChargeCommand calcMgmtChg = threadCore.newCommand(CalculateManagementChargeCommand.class);
                calcMgmtChg.setPolicyArgRet(policy);
                calcMgmtChg.invoke();
                return calcMgmtChg.getPolicyArgRet().getAssessmentSheet();
            });

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            // Merge new lines from each cloned sheet back into the original
            mergeSheetLines(originalSheet, taxFuture.get(), "CalculateTax");
            mergeSheetLines(originalSheet, commissionFuture.get(), "CalculateCommission");
            mergeSheetLines(originalSheet, brokerageFuture.get(), "CalculateBrokerage");
            mergeSheetLines(originalSheet, mgmtChargeFuture.get(), "CalculateManagementCharge");

            policy.setAssessmentSheet(originalSheet);
        } catch (CloneNotSupportedException e) {
            return executeCalculationsSequentially(policy);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new PreconditionException("Parallel calculation failed: " + e.getMessage());
        }

        return policy;
    }

    /**
     * Merge new lines from a cloned sheet back into the original sheet.
     * Only lines added by the specified origin that don't exist in the original are merged.
     */
    private void mergeSheetLines(AssessmentSheet original, AssessmentSheet cloned, String origin) {
        original.setLockingActor(origin);
        for (Map.Entry<String, AssessmentLine> entry : cloned.getAssessmentLine().entrySet()) {
            if (origin.equals(entry.getValue().getOrigin()) && !original.getAssessmentLine().containsKey(entry.getKey())) {
                original.addLine(entry.getValue());
            }
        }
        original.clearLockingActor();
    }

    public boolean isParallelExecutionEnabled() {
        return parallelExecutionEnabled;
    }

    public void setParallelExecutionEnabled(boolean parallelExecutionEnabled) {
        this.parallelExecutionEnabled = parallelExecutionEnabled;
    }
}


