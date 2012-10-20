package com.oracle.hudson.plugins;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.BuildWrapper.Environment;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.oracle.hudson.plugins.AdeViewLauncherDecorator.EnvironmentImpl;

/**
 * package private module to support caching the ADE environment
 * after creating a new view (or using an existing one)
 * 
 * @author tagarwal
 *
 */
class AdeEnvironmentCache {
	
	private Boolean isActive;
	
	AdeEnvironmentCache(Boolean isActive) {
		this.isActive = isActive;
	}
	public boolean isActive() {
		return isActive;
	}
	
	Environment createEnvironment(
			@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener, 
			AdeViewLauncherDecorator ade) 
			throws IOException, InterruptedException {
		String workspace = ade.getWorkspace();
		ProcStarter uvProcStarter = launcher.launch()
				.cmds("ade","useview",ade.getViewName(build),"-exec","printenv >" + workspace + "/adeEnv")
				.stdout(listener)
				.stderr(listener.getLogger())
				.envs(ade.getEnvOverrides(build));
		Proc uvProc = launcher.launch(uvProcStarter);
		uvProc.join();
		//now read the env variables into Env and return that for all builds
		Scanner sc = new Scanner(new File(workspace + "/adeEnv")).useDelimiter("=");
		Map<String,String> envMap = new HashMap<String,String>();
		String key,value;
		while (sc.hasNextLine()) {
			key =sc.next();
			sc.skip("=");
			value= sc.nextLine();
			envMap.put(key,value);
		}

		sc.close();

		EnvironmentImpl retEnv = ade.new EnvironmentImpl(launcher,build);
		retEnv.setEnvMapToAdd(envMap);

		return retEnv;
	}
}
