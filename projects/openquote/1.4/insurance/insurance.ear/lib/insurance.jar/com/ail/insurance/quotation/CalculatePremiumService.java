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

import com.ail.annotation.Configurable;
import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.CoreProxy;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.quotation.AssessRiskService.AssessRiskCommand;
import com.ail.insurance.quotation.CalculateBrokerageService.CalculateBrokerageCommand;
import com.ail.insurance.quotation.CalculateCommissionService.CalculateCommissionCommand;
import com.ail.insurance.quotation.CalculateManagementChargeService.CalculateManagementChargeCommand;
import com.ail.insurance.quotation.CalculateTaxService.CalculateTaxCommand;
import com.ail.insurance.quotation.RefreshAssessmentSheetsService.RefreshAssessmentSheetsCommand;

@Configurable
@ServiceImplementation
public class CalculatePremiumService extends Service<CalculatePremiumService.CalculatePremiumArgument> {
    private static final long serialVersionUID = 7959054658477631252L;

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

        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("CalculatePremium");
        rasc.invoke();
        policy=rasc.getPolicyArgRet();

        if (policy.isMarkedForDecline()) {
            policy.setStatus(DECLINED);
        }
        else if (policy.isMarkedForRefer()) {
            policy.setStatus(REFERRED);
        }
        else {
            policy.setStatus(QUOTATION);
        }
    }
}


