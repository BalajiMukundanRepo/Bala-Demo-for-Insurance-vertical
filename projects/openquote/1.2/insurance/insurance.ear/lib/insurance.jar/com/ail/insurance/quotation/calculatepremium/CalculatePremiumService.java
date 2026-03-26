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

package com.ail.insurance.quotation.calculatepremium;

import static com.ail.insurance.policy.PolicyStatus.APPLICATION;
import static com.ail.insurance.policy.PolicyStatus.DECLINED;
import static com.ail.insurance.policy.PolicyStatus.QUOTATION;
import static com.ail.insurance.policy.PolicyStatus.REFERRED;

import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.CoreProxy;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.Version;
import com.ail.core.command.CommandArg;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.quotation.assessrisk.AssessRiskCommand;
import com.ail.insurance.quotation.calculatebrokerage.CalculateBrokerageCommand;
import com.ail.insurance.quotation.calculatecommission.CalculateCommissionCommand;
import com.ail.insurance.quotation.calculatemanagementcharge.CalculateManagementChargeCommand;
import com.ail.insurance.quotation.calculatetax.CalculateTaxCommand;
import com.ail.insurance.quotation.refreshassessmentsheets.RefreshAssessmentSheetsCommand;

/**
 * @version $Revision: 1.5 $
 * @state $State: Exp $
 * @date $Date: 2007/03/27 23:00:27 $
 * @source $Source: /home/bob/CVSRepository/projects/insurance/insurance.ear/insurance.jar/com/ail/insurance/quotation/calculatepremium/CalculatePremiumService.java,v $
 * @stereotype service
 */
public class CalculatePremiumService extends Service {
    private static final long serialVersionUID = 7959054658477631252L;
    private CalculatePremiumArg args = null;
    private Core core = null;

    /** Default constructor */
    public CalculatePremiumService() {
        core=new Core(this);
    }

    /**
     * Getter to fetch the entry point's code. This method is demanded by the EntryPoint class.
     * @return This entry point's instance of Core.
     */
    public Core getCore() {
        return core;
    }

    /**
     * Fetch the version of this entry point.
     * @return A version object describing the version of this entry point.
     */
    public Version getVersion() {
        com.ail.core.Version v = (com.ail.core.Version) core.newType("Version");
        v.setCopyright("Copyright Applied Industrial Logic Limited 2002. All rights reserved.");
        v.setDate("$Date: 2007/03/27 23:00:27 $");
        v.setSource("$Source: /home/bob/CVSRepository/projects/insurance/insurance.ear/insurance.jar/com/ail/insurance/quotation/calculatepremium/CalculatePremiumService.java,v $");
        v.setState("$State: Exp $");
        v.setVersion("$Revision: 1.5 $");
        return v;
    }

    /**
     * Setter used to the set the entry points arguments.
     * @param args for invoke
     */
    public void setArgs(CommandArg args) {
        this.args = (CalculatePremiumArg) args;
    }

    /**
     * Getter returning the arguments used by this entry point.
     * @return An instance of $Name:  $Args.
     */
    public CommandArg getArgs() {
        return args;
    }


    /** The 'business logic' of the entry point. */
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
        
        AssessRiskCommand arc=(AssessRiskCommand)core.newCommand("AssessRisk");
        arc.setPolicyArgRet(policy);
        arc.invoke();
        policy=arc.getPolicyArgRet();
        
        if (policy.getAssessmentSheet() == null) {
            throw new PreconditionException("policy.assessmentSheet==null");
        }

        // calculate the assessment sheet so that the other calc services get to see premiums etc.
        RefreshAssessmentSheetsCommand rasc=(RefreshAssessmentSheetsCommand)core.newCommand("RefreshAssessmentSheets");
        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("CalculatePremium");
        rasc.invoke();
        policy=rasc.getPolicyArgRet();

        // calc tax
        CalculateTaxCommand calcTax=(CalculateTaxCommand)core.newCommand("CalculateTax");
        calcTax.setPolicyArgRet(policy);
        calcTax.invoke();
        policy=calcTax.getPolicyArgRet();

        // calc commission
        CalculateCommissionCommand calcCommission=(CalculateCommissionCommand)core.newCommand("CalculateCommission");
        calcCommission.setPolicyArgRet(policy);
        calcCommission.invoke();
        policy=calcCommission.getPolicyArgRet();

        // calc brokerage
        CalculateBrokerageCommand calcBrokerage=(CalculateBrokerageCommand)core.newCommand("CalculateBrokerage");
        calcBrokerage.setPolicyArgRet(policy);
        calcBrokerage.invoke();
        policy=calcBrokerage.getPolicyArgRet();

        // calc management charge
        CalculateManagementChargeCommand calcMgmtChg=(CalculateManagementChargeCommand)core.newCommand("CalculateManagementCharge");
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


