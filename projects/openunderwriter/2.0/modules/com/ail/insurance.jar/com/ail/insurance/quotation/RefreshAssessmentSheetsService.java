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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.AssessmentLine;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.AssessmentSheetList;
import com.ail.insurance.policy.AssessmentStage;
import com.ail.insurance.policy.CalculationLine;
import com.ail.insurance.policy.Policy;

@ServiceImplementation
public class RefreshAssessmentSheetsService extends Service<RefreshAssessmentSheetsService.RefreshAssessmentSheetsArgument> {
    private static final long serialVersionUID = 3642886757932052003L;

    @ServiceArgument
    public interface RefreshAssessmentSheetsArgument extends Argument {
        /**
         * Getter for the policyArgRet property. The policy to refresh assessment sheets on.
         * @return Value of policyArgRet, or null if it is unset
         */
        Policy getPolicyArgRet();

        /**
         * Setter for the policyArgRet property. * @see #getPolicyArgRet
         * @param policyArgRet new value for property.
         */
        void setPolicyArgRet(Policy policyArgRet);

        /**
         * Getter for the originArg property. Used to tie assessment sheet entries to the service (owner) which originated them.
         * @return Value of originArg, or null if it is unset
         */
        String getOriginArg();

        /**
         * Setter for the originArg property. * @see #getOriginArg
         * @param originArg new value for property.
         */
        void setOriginArg(String originArg);
    }

    @ServiceCommand(defaultServiceClass=RefreshAssessmentSheetsService.class)
    public interface RefreshAssessmentSheetsCommand extends Command, RefreshAssessmentSheetsArgument {
    }

    /**
     * Process the contents of an AssessmentSheet. Run through the contents of the sheet checking each line
     * in order of priority. If a line hasn't already been calculated (i.e. it doesn't appear in the <i>processed</i>
     * collection) then it's <i>calculate()</i> method is called. If that call returns <i>true</i> the line is assumed
     * to have been processed and will be added to the <i>processed<i> list and marked with an integer indicating the 
     * order it was processed in relation to other lines.
     * @param sheet The sheet to process
     * @param sheets Collection of all sheets being processed
     * @param processed Collection of the AssessmentLines that have been processed so far.
     * @param calculationCountOffset Index to start numbering processed lines from. 
     * @return true If any line is processed, false otherwise
     */
    private int processSheet(AssessmentSheet sheet, AssessmentSheetList sheets, Set<AssessmentLine> processed, int calculationCountOffset) {
        int processedLineCount=calculationCountOffset+1;
        CalculationLine cl=null;
        ArrayList<CalculationLine> sortedLines=null;

        // get the CalculationLines in priority order
        sortedLines=new ArrayList<CalculationLine>(sheet.getLinesOfType(CalculationLine.class).values());
        Collections.sort(sortedLines);
        
        // make sure that each line knows about the assessment sheet it is in
        for(CalculationLine line: sortedLines) {
            line.setAssessmentSheet(sheet);
        }
        
        // iterate through the lines
        for(Iterator<CalculationLine> it=sortedLines.iterator() ; it.hasNext() ; ) {
            cl=it.next();

            // if we've processed this line already, skip to the next (O(1) HashSet lookup).
            if (processed.contains(cl)) {
                continue;
            }

            // calculate the line, if we get a true back then the line has been done
            if (cl.calculate(sheets, sheet)==true) {
                processed.add(cl);
                cl.setCalculatedOrder(processedLineCount++);
            }
        }

        return processedLineCount;
    }

    private int iterateOverSheets(AssessmentSheetList sheets, Set<AssessmentLine> processed, int calculationOrder) {
        boolean again=false;
        // Track which sheets had changes so only those are re-processed in subsequent iterations
        Set<AssessmentSheet> dirtySheets = new HashSet<AssessmentSheet>(sheets.getSheets());

        do {
            again=false;
            Set<AssessmentSheet> nextDirtySheets = new HashSet<AssessmentSheet>();

            for(Iterator<AssessmentSheet> it=dirtySheets.iterator() ; it.hasNext() ; ) {
                AssessmentSheet sheet = it.next();
                int count=processSheet(sheet, sheets, processed, calculationOrder);
                
                if (count!=0) {
                    calculationOrder+=count;
                    again=true;
                    nextDirtySheets.add(sheet);
                }
            }
            dirtySheets = nextDirtySheets;
        } while(again==true);

        return calculationOrder;
    }
    
    private void processAssessmentSheets(Policy policy) {
        int calculationOrder=0;
        AssessmentSheetList sheets=new AssessmentSheetList(policy);
        Set<AssessmentLine> processed=new HashSet<AssessmentLine>();

        // execute all the control lines appropriate to the before phase
        sheets.executeControlLinesForAssessmentStage(AssessmentStage.BEFORE_RATING);

        // the following loop performs all the rating calcs, so set the phase accordingly
        sheets.setAssessmentStage(AssessmentStage.RATING);
        
        calculationOrder=iterateOverSheets(sheets, processed, calculationOrder);

        // execute all the control lines appropriate to the after phase
        sheets.executeControlLinesForAssessmentStage(AssessmentStage.AFTER_RATING);

        calculationOrder=iterateOverSheets(sheets, processed, calculationOrder);
    }

    @Override
    public void invoke() throws PreconditionException {
        // check preconditions
        if (args.getPolicyArgRet()==null) {
            throw new PreconditionException("args.getPolicyArgRet()==null");
        }

        if (args.getOriginArg()==null || args.getOriginArg().length()==0) {
            throw new PreconditionException("args.getOriginArg()==null || args.getOriginArg().length()==0");
        }

        Policy policy=args.getPolicyArgRet();

        // remove only lines that this origin added in the past
        policy.removeAssessmentLinesByOrigin(args.getOriginArg());

        // Lines may have been fired in a previous run, reset them so they can fire again.
        policy.resetAssessmentControlLines();
        
        // lock the sheets to this origin
        policy.lockAllAssessmentSheets(args.getOriginArg());

        // perform the refresh
        processAssessmentSheets(args.getPolicyArgRet());

        // unlock the assessment sheets
        policy.unlockAllAssessmentSheets();
    }
}


