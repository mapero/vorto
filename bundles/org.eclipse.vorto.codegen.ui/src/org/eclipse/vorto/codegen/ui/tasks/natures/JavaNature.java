/*******************************************************************************
 *  Copyright (c) 2015, 2016 Bosch Software Innovations GmbH and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v1.0 which accompany this distribution.
 *   
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *  The Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *   
 *  Contributors:
 *  Bosch Software Innovations GmbH - Please refer to git log
 *******************************************************************************/

package org.eclipse.vorto.codegen.ui.tasks.natures;

import org.eclipse.vorto.codegen.ui.tasks.ClasspathConfiguration;

/**
 * 
 * Java Configuration of an Eclipse Project
 * 
 */
public class JavaNature extends CustomNature {

	public JavaNature(String[] srcFolders) {
		super("org.eclipse.jdt.core.javanature");
	}

	public JavaNature(String[] srcFolders,
			ClasspathConfiguration classpathConfiguration) {
		super("org.eclipse.jdt.core.javanature");
		classpathConfiguration.setSourceFolders(srcFolders);
		classpathConfiguration.addEntry(ClasspathConfiguration.JAVA_JRE);
	}

}
