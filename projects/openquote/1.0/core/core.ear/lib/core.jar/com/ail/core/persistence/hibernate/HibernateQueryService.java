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

import com.ail.core.Core;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.Type;
import com.ail.core.Version;
import com.ail.core.command.CommandArg;
import com.ail.core.persistence.QueryArg;
import com.ail.core.persistence.QueryException;

/**
 * Implementation of the query service for Hibernate
 * @version $Revision: 1.4 $
 * @state $State: Exp $
 * @date $Date: 2006/07/15 15:01:44 $
 * @source $Source: /home/bob/CVSRepository/projects/core/core.ear/core.jar/com/ail/core/persistence/hibernate/HibernateQueryService.java,v $
 * @stereotype service
 */
public class HibernateQueryService extends Service {
    private QueryArg args = null;
    private Core core;
    
    /** Default constructor */
    public HibernateQueryService() {
       super();
       core=new Core(this);
    }

    /**
     * Getter to fetch the entry point's core. This method is demanded by the EntryPoint class.
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
        com.ail.core.Version v = (com.ail.core.Version) getCore().newType("Version");
        v.setCopyright("Copyright Applied Industrial Logic Limited 2003. All rights reserved.");
        v.setDate("$Date: 2006/07/15 15:01:44 $");
        v.setSource("$Source: /home/bob/CVSRepository/projects/core/core.ear/core.jar/com/ail/core/persistence/hibernate/HibernateQueryService.java,v $");
        v.setState("$State: Exp $");
        v.setVersion("$Revision: 1.4 $");
        return v;
    }

    /**
     * Setter used to the set the entry points arguments.
     * @param args for invoke
     */
    public void setArgs(CommandArg args) {
        this.args = (QueryArg)args;
    }

    /**
     * Getter returning the arguments used by this entry point.
     * @return An instance of QueryArgs.
     */
    public CommandArg getArgs() {
        return args;
    }

    /** The 'business logic' of the entry point. */
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


