package com.oracle.hudson.plugins;

import hudson.model.BuildBadgeAction;
import hudson.model.Result;

public class IntegrationBadgeAction implements BuildBadgeAction {
	
	enum Type {BUILD,PROMOTE}
	private final Type type;
	private final Boolean successful;
	
	IntegrationBadgeAction(Type t, Boolean b) {
		type = t;
		successful = b;
	}

	public String getDisplayName() {
		return type.name();
	}

	public String getIconFileName() {
		return (this.successful)?"blue.png":"red.png";
	}
	
	public String getDescription() {
		switch(type) {
		case BUILD:
			return "build portion "+(this.successful?"was successful":"failed");
		case PROMOTE:
		default:
			return "promote task completed "+(this.successful?"successfully":"with errors");
		}
		
	}
	
	public String getUrlName() {
		return type.name();
	}
}
