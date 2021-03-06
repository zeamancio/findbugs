/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2003-2005, University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs;

import java.io.IOException;

import edu.umd.cs.findbugs.xml.OutputStreamXMLOutput;

/**
 * Report warnings as an XML document.
 * 
 * @author David Hovemeyer
 */
public class XMLBugReporter extends BugCollectionBugReporter {

	public XMLBugReporter(Project project) {
		super(project);
	}

	public void setAddMessages(boolean enable) {
		getBugCollection().setWithMessages(enable);
	}
	
	@Override
	public void finish() {
		try {
		Project project = getProject();
		if (project == null) 
			throw new NullPointerException("No project");
		getBugCollection().writeXML(outputStream);
		} catch (IOException e) {
			throw new FatalException("Error writing XML output", e);
		}
	}

	/**
     * @param xmlMinimal
     */
    public void setMinimalXML(boolean xmlMinimal) {
    	getBugCollection().setMinimalXML(xmlMinimal);
	    
    }

}

// vim:ts=4
