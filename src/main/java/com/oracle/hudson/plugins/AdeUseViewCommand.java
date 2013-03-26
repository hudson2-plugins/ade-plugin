package com.oracle.hudson.plugins;

import hudson.model.AbstractBuild;

import java.util.ArrayList;
import java.util.List;

class AdeUseViewCommand {
	
	private List<String> args = new ArrayList<String>();
	
	AdeUseViewCommand(AbstractBuild build, AdeViewLauncherDecorator ade) {
		args.add("ade");
		args.add("useview");
		if (!ade.usesEnv()) {
			args.add("-noenv");
		}
		args.add(ade.getViewName(build));
		args.add("-exec");
	}

	String[] getArgs() {
		return args.toArray(new String[]{});
	}
}
