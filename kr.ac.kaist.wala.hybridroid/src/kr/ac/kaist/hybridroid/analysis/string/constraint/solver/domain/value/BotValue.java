/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package kr.ac.kaist.hybridroid.analysis.string.constraint.solver.domain.value;

import kr.ac.kaist.hybridroid.analysis.string.constraint.solver.domain.IDomain;

public class BotValue implements IValue {

	private static BotValue instance;
	public static BotValue getInstance(){
		if(instance == null)
			instance = new BotValue();
		return instance;
	}
	
	protected BotValue(){}
	
	@Override
	public IValue clone() {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public IValue weakUpdate(IValue v) {
		// TODO Auto-generated method stub
		return v;
	}

	@Override
	public IDomain getDomain() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public String toString(){
		return "BOT";
	}
}