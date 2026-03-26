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

package com.ail.core.persistence.hibernate;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.classic.Session;

import com.ail.annotation.ServiceImplementation;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.Type;
import com.ail.core.persistence.QueryService.QueryArgument;
import com.ail.core.persistence.QueryException;

/**
 * Implementation of the query service for Hibernate
 */
@ServiceImplementation
public class HibernateQueryService extends Service<QueryArgument> {
    @Override
    @SuppressWarnings("unchecked")
    public void invoke() throws PreconditionException,QueryException {

	    if (args.getQueryNameArg()==null || args.getQueryNameArg().length()==0) {
	        throw new PreconditionException("args.getQueryArg()==null");
	    }

        Session session=null;
        
        try {
            session = HibernateFunctions.getSessionFactory().getCurrentSession(); 

            Query q=session.getNamedQuery(args.getQueryNameArg());
            
            for(int argNo=0 ; argNo<args.getQueryArgumentsArg().length ; argNo++) {
                q.setParameter(argNo, args.getQueryArgumentsArg()[argNo]);
            }
            
            java.util.List results = q.list();
            args.setResultsListRet(results);
            
            if (results.size()==1) {
                args.setUniqueResultRet((Type)results.get(0));
            }
		} 
        catch (HibernateException e) {
			throw new QueryException("Hibernate could not query the database",e);
		}
    }
}


