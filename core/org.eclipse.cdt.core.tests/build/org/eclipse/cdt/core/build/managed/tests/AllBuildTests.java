/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.cdt.core.build.managed.tests;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.build.managed.BuildException;
import org.eclipse.cdt.core.build.managed.IConfiguration;
import org.eclipse.cdt.core.build.managed.IOption;
import org.eclipse.cdt.core.build.managed.IOptionCategory;
import org.eclipse.cdt.core.build.managed.IManagedBuildInfo;
import org.eclipse.cdt.core.build.managed.ITarget;
import org.eclipse.cdt.core.build.managed.ITool;
import org.eclipse.cdt.core.build.managed.ManagedBuildManager;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.IScannerInfoChangeListener;
import org.eclipse.cdt.core.parser.IScannerInfoProvider;
import org.eclipse.cdt.internal.core.build.managed.ToolReference;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;

public class AllBuildTests extends TestCase {
	private static final boolean boolVal = true;
	private static final String testConfigName = "test.config.override";
	private static final String enumVal = "Another Enum";
	private static final String[] listVal = {"_DEBUG", "/usr/include", "libglade.a"};
	private static final String projectName = "ManagedBuildTest";
	private static final String rootExt = "toor";
	private static final String stringVal = "-c -Wall";
	private static final String subExt = "bus";

	public AllBuildTests(String name) {
		super(name);
	}
	
	public static Test suite() {
		TestSuite suite = new TestSuite(AllBuildTests.class.getName());
		
		suite.addTest(new AllBuildTests("testExtensions"));
		suite.addTest(new AllBuildTests("testProject"));
		suite.addTest(new AllBuildTests("testConfigurations"));
		suite.addTest(new AllBuildTests("testTargetArtifacts"));
		suite.addTest(new AllBuildTests("testScannerInfoInterface"));
		suite.addTest(new AllBuildTests("cleanup"));
		
		return suite;
	}

	/**
	 * Navigates through the build info as defined in the extensions
	 * defined in this plugin
	 */
	public void testExtensions() throws Exception {
		ITarget testRoot = null;
		ITarget testSub = null;
		
		// Note secret null parameter which means just extensions
		ITarget[] targets = ManagedBuildManager.getDefinedTargets(null);

		for (int i = 0; i < targets.length; ++i) {
			ITarget target = targets[i];
			
			if (target.getName().equals("Test Root")) {
				testRoot = target;
				checkRootTarget(testRoot, "x");
				
			} else if (target.getName().equals("Test Sub")) {
				testSub = target;
				checkSubTarget(testSub);
			}
		}
		
		assertNotNull(testRoot);
		assertNotNull(testSub);
	}

	/**
	 * The purpose of this test is to exercise the build path info interface.
	 * To get to that point, a new target/config has to be created in the test
	 * project and the default configuration changed.
	 *  
	 * @throws CoreException
	 */
	public void testScannerInfoInterface(){
		// Open the test project
		IProject project = null;
		try {
			project = createProject(projectName);
		} catch (CoreException e) {
			fail("Failed to open project: " + e.getLocalizedMessage());
		}
		
		// Create a new target in the project based on the sub target
		ITarget baseTarget = ManagedBuildManager.getTarget(project, "test.sub");
		assertNotNull(baseTarget);
		ITarget newTarget = null;
		try {
			newTarget = ManagedBuildManager.createTarget(project, baseTarget);
		} catch (BuildException e) {
			fail("Failed adding new target to project: " + e.getLocalizedMessage());
		}
		assertNotNull(newTarget);
		// Copy over the configs
		IConfiguration[] baseConfigs = baseTarget.getConfigurations();
		for (int i = 0; i < baseConfigs.length; ++i) {
			newTarget.createConfiguration(baseConfigs[i], baseConfigs[i].getId() + "." + i);
		}
				
		// Change the default configuration to the sub config
		IConfiguration[] configs = newTarget.getConfigurations();
		assertEquals(3, configs.length);
		IManagedBuildInfo buildInfo = ManagedBuildManager.getBuildInfo(project);
		buildInfo.setDefaultConfiguration(newTarget.getConfiguration("sub.config.2"));

		// Use the plugin mechanism to discover the supplier of the path information
		IExtensionPoint extensionPoint = CCorePlugin.getDefault().getDescriptor().getExtensionPoint("ScannerInfoProvider");
		if (extensionPoint == null) {
			fail("Failed to retrieve the extension point ScannerInfoProvider.");
		}
		IExtension[] extensions = extensionPoint.getExtensions();
		IScannerInfoProvider provider = null;
		// Find the first IScannerInfoProvider that supplies build info for the project
		for (int i = 0; i < extensions.length && provider == null; i++) {
			IExtension extension = extensions[i];
			IConfigurationElement[] elements = extension.getConfigurationElements();
			for (int j = 0; j < elements.length; ++j) {
				IConfigurationElement element = elements[j];
				if (element.getName().equals("provider")) { 
					// Check if it handles the info for the project
					try {
						IScannerInfoProvider temp = (IScannerInfoProvider)element.createExecutableExtension("class");
						if (temp.managesResource(project)) {
							provider = temp;
							break;
						}
					} catch (CoreException e) {
						fail("Failed retrieving scanner info provider from plugin: " + e.getLocalizedMessage());
					}
				}
			}
		}
		assertNotNull(provider);
		provider.subscribe(project, new IScannerInfoChangeListener () {
			public void changeNotification(IResource project, IScannerInfo info) {
				// Test the symbols 
				Map definedSymbols = info.getDefinedSymbols();
				assertTrue(definedSymbols.containsKey("DEBUG"));
				assertTrue(definedSymbols.containsKey("GNOME"));
				assertTrue(definedSymbols.containsValue("ME"));
				assertEquals((String)definedSymbols.get("DEBUG"), "");
				assertEquals((String)definedSymbols.get("GNOME"), "ME");
				// Test the includes path
				String[] expectedPaths = {"/usr/include", "/opt/gnome/include", "/home/tester/include"};
				String[] actualPaths = info.getIncludePaths();
				assertTrue(Arrays.equals(expectedPaths, actualPaths));
			}
		});
		
		// Add some defined symbols programmatically
		String[] expectedSymbols = {"DEBUG", "GNOME = ME "};
		IConfiguration defaultConfig = buildInfo.getDefaultConfiguration(newTarget);
		ITool[] tools = defaultConfig.getTools();
		ITool subTool = null;
		for (int i = 0; i < tools.length; i++) {
			ITool tool = tools[i];
			if("tool.sub".equalsIgnoreCase(tool.getId())) {
				subTool = tool;
				break;
			}
		}
		assertNotNull(subTool);
		IOption symbolOpt = null;
		IOption[] opts = subTool.getOptions();
		for (int i = 0; i < opts.length; i++) {
			IOption option = opts[i];
			if (option.getValueType() == IOption.PREPROCESSOR_SYMBOLS) {
				symbolOpt = option;
				break;
			}
		}
		assertNotNull(symbolOpt);
		ManagedBuildManager.setOption(defaultConfig, symbolOpt, expectedSymbols);
	}
	
	/**
	 * Create a new configuration based on one defined in the plugin file.
	 * Overrides all of the configuration settings. Saves, closes, and reopens 
	 * the project. Then calls a method to check the overridden options.
	 * 
	 * Tests creating a new configuration.
	 * Tests setting options.
	 * Tests persisting overridden options between project sessions.
	 * 
	 */
	public void testConfigurations() throws CoreException, BuildException {
		// Open the test project
		IProject project = createProject(projectName);
		
		// Make sure there is one and only one target with 2 configs
		ITarget[] definedTargets = ManagedBuildManager.getTargets(project);
		assertEquals(1, definedTargets.length);
		ITarget rootTarget = definedTargets[0];
		IConfiguration[] definedConfigs = rootTarget.getConfigurations(); 		
		assertEquals(2, definedConfigs.length);
		IConfiguration baseConfig = definedConfigs[0];
		
		// Create a new configuration
		IConfiguration newConfig = rootTarget.createConfiguration(baseConfig, testConfigName);
		assertEquals(3, rootTarget.getConfigurations().length);

		// There is only one tool
		ITool[] definedTools = newConfig.getTools();
		assertEquals(1, definedTools.length);
		ITool rootTool = definedTools[0];
		
		// Override options in the new configuration
		IOptionCategory topCategory = rootTool.getTopOptionCategory();
		assertEquals("Root Tool", topCategory.getName());
		IOption[] options = topCategory.getOptions(null);
		assertEquals(2, options.length);
		ManagedBuildManager.setOption(newConfig, options[0], listVal);
		ManagedBuildManager.setOption(newConfig, options[1], boolVal);

		IOptionCategory[] categories = topCategory.getChildCategories();
		assertEquals(1, categories.length);
		options = categories[0].getOptions(null);
		assertEquals(2, options.length);
		ManagedBuildManager.setOption(newConfig, options[0], stringVal);
		ManagedBuildManager.setOption(newConfig, options[1], enumVal);

		// Save, close, reopen and test again
		ManagedBuildManager.saveBuildInfo(project);
		project.close(null);
		ManagedBuildManager.removeBuildInfo(project);
		project.open(null);

		// Test the values in the new configuration
		checkOptionReferences(project);
	}
	
	/**
	 * @throws CoreException
	 * @throws BuildException
	 */
	public void testProject() throws BuildException {
		// Create new project
		IProject project = null;
		try {
			project = createProject(projectName);
		} catch (CoreException e) {
			fail("Test failed on project creation: " + e.getLocalizedMessage());
		}
		// There should not be any targets defined for this project yet
		assertEquals(0, ManagedBuildManager.getTargets(project).length);
		
		// Find the base target definition
		ITarget targetDef = ManagedBuildManager.getTarget(project, "test.root");
		assertNotNull(targetDef);
		
		// Create the target for our project that builds a dummy executable
		ITarget newTarget = ManagedBuildManager.createTarget(project, targetDef);
		assertEquals(newTarget.getName(), targetDef.getName());
		assertFalse(newTarget.equals(targetDef));
		String buildArtifactName = projectName + "." + newTarget.getDefaultExtension();
		newTarget.setBuildArtifact(buildArtifactName);
		
		ITarget[] targets = ManagedBuildManager.getTargets(project);
		assertEquals(1, targets.length);
		ITarget target = targets[0];
		assertEquals(target, newTarget);
		assertFalse(target.equals(targetDef));

		// Copy over the configs
		IConfiguration defaultConfig = null;
		IConfiguration[] configs = targetDef.getConfigurations();
		for (int i = 0; i < configs.length; ++i) {
			// Make the first configuration the default 
			if (i == 0) {
				defaultConfig = target.createConfiguration(configs[i], target.getId() + "." + i);
			} else {
				target.createConfiguration(configs[i], target.getId() + "." + i);
			}
		}
		ManagedBuildManager.setDefaultConfiguration(project, defaultConfig);		
		checkRootTarget(target, "x");
		
		// Override the "String Option in Category" option value
		configs = target.getConfigurations();
		ITool[] tools = configs[0].getTools();
		IOptionCategory topCategory = tools[0].getTopOptionCategory();
		IOptionCategory[] categories = topCategory.getChildCategories();
		IOption[] options = categories[0].getOptions(configs[0]);
		configs[0].setOption(options[0], "z");
		options = categories[0].getOptions(null);
		assertEquals("x", options[0].getStringValue());
		options = categories[0].getOptions(configs[0]);
		assertEquals("z", options[0].getStringValue());
		
		// Save, close, reopen and test again
		ManagedBuildManager.saveBuildInfo(project);
		try {
			project.close(null);
		} catch (CoreException e) {
			fail("Failed on project close: " + e.getLocalizedMessage());
		}
		ManagedBuildManager.removeBuildInfo(project);
		try {
			project.open(null);
		} catch (CoreException e) {
			fail("Failed on project open: " + e.getLocalizedMessage());
		}
		
		// Test that the default config was remembered
		IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(project);
		assertEquals(defaultConfig.getId(), info.getDefaultConfiguration(target).getId());

		// Get the targets
		targets = ManagedBuildManager.getTargets(project);
		assertEquals(1, targets.length);
		// See if the artifact name is remembered
		assertEquals(targets[0].getArtifactName(), buildArtifactName);
		// Check the rest of the default information
		checkRootTarget(targets[0], "z");
		
		// Now test the information the makefile builder needs
		checkBuildTestSettings(info);
		ManagedBuildManager.removeBuildInfo(project);
	}
	
	/**
	 * Tests the tool settings through the interface the makefile generator
	 * uses.
	 * 
	 * @param project
	 */
	private void checkBuildTestSettings(IManagedBuildInfo info) {
		String ext1 = "foo";
		String ext2 = "bar";
		String badExt = "cpp";
		String expectedOutput = "toor";
		String expectedCmd = "doIt";
		
		assertNotNull(info);
		assertEquals(info.getBuildArtifactName(), projectName + "." + rootExt);
		
		// There should be a default configuration defined for the project
		ITarget buildTarget = info.getDefaultTarget();
		assertNotNull(buildTarget);
		assertEquals(buildTarget.getId(), "test.root.1");
		IConfiguration buildConfig = info.getDefaultConfiguration(buildTarget);
		assertNotNull(buildConfig);
		assertEquals(buildConfig.getId(), "test.root.1.0");
				
		// The default target should be the same as the one-and-only target in the project
		List targets = info.getTargets();
		assertEquals(targets.size(), 1);
		ITarget target = (ITarget) targets.get(0);
		assertEquals(target, buildTarget);
		
		// Check that tool handles resources with extensions foo and bar by building a baz
		assertEquals(info.getOutputExtension(ext1), expectedOutput);
		assertEquals(info.getOutputExtension(ext2), expectedOutput);
		
		// Check that it ignores others based on filename extensions
		assertNull(info.getOutputExtension(badExt));
		
		// Now see what the tool command line invocation is for foo and bar
		assertEquals(info.getToolForSource(ext1), expectedCmd);
		assertEquals(info.getToolForSource(ext2), expectedCmd);
		// Make sure that there is no tool to build files of type foo and bar
		assertNull(info.getToolForTarget(ext1));
		assertNull(info.getToolForTarget(ext2));
		
		// There is no target that builds toor
		assertNull(info.getToolForSource(expectedOutput));
		// but there is one that produces it
		assertEquals(info.getToolForTarget(expectedOutput), expectedCmd);
		
		// Now check the build flags
		assertEquals(info.getFlagsForSource(ext1), "-La -Lb z -e1");
		assertEquals(info.getFlagsForSource(ext1), info.getFlagsForSource(ext2));
		
	}
	
	/**
	 * Tests that overridden options are properly read into build model.
	 * Test that option values that are not overridden remain the same.
	 * 
	 * @param project The project to get build model information for.
	 * @throws BuildException
	 */
	private void checkOptionReferences(IProject project) throws BuildException {
		// Get the targets out of the project
		ITarget[] definedTargets = ManagedBuildManager.getTargets(project);
		assertEquals(1, definedTargets.length);
		ITarget rootTarget = definedTargets[0];

		// Now get the configs
		IConfiguration[] definedConfigs = rootTarget.getConfigurations(); 		
		assertEquals(3, definedConfigs.length);
		IConfiguration newConfig = rootTarget.getConfiguration(testConfigName);
		assertNotNull(newConfig);

		// Now get the tool options and make sure the values are correct		
		ITool[] definedTools = newConfig.getTools();
		assertEquals(1, definedTools.length);
		ITool rootTool = definedTools[0];

		// Check that the options in the new config contain overridden values
		IOption[] rootOptions = rootTool.getOptions();
		assertEquals(4, rootOptions.length);
		// First is the new list
		assertEquals("List Option in Top", rootOptions[0].getName());
		assertEquals(IOption.STRING_LIST, rootOptions[0].getValueType());
		String[] list = rootOptions[0].getStringListValue();
		assertEquals(3, list.length);
		assertTrue(Arrays.equals(listVal, list));
		assertEquals(rootOptions[0].getCommand(), "-L");
		// Next option is a boolean in top
		assertEquals("Boolean Option in Top", rootOptions[1].getName());
		assertEquals(IOption.BOOLEAN, rootOptions[1].getValueType());
		assertEquals(boolVal, rootOptions[1].getBooleanValue());
		assertEquals("-b", rootOptions[1].getCommand());
		// Next option is a string category
		assertEquals("String Option in Category", rootOptions[2].getName());
		assertEquals(IOption.STRING, rootOptions[2].getValueType());
		assertEquals(stringVal, rootOptions[2].getStringValue());
		// Final option is an enumerated
		assertEquals("Enumerated Option in Category", rootOptions[3].getName());
		assertEquals(IOption.ENUMERATED, rootOptions[3].getValueType());
		String selEnum = rootOptions[3].getSelectedEnum();
		assertEquals(enumVal, selEnum);
		String[] enums = rootOptions[3].getApplicableValues();
		assertEquals(2, enums.length);
		assertEquals("Default Enum", enums[0]);
		assertEquals("Another Enum", enums[1]);
		assertEquals("-e1", rootOptions[3].getEnumCommand(enums[0]));
		assertEquals("-e2", rootOptions[3].getEnumCommand(enums[1]));
		assertEquals("-e2", rootOptions[3].getEnumCommand(selEnum));
	}
	
	/*
	 * Do a full sanity check on the root target.
	 */
	private void checkRootTarget(ITarget target, String oicValue) throws BuildException {
		// Target stuff
		assertTrue(target.isTestTarget());
		assertEquals(target.getDefaultExtension(), rootExt);
		
		// Tools
		ITool[] tools = target.getTools();
		// Root Tool
		ITool rootTool = tools[0];
		assertEquals("Root Tool", rootTool.getName());
		// 4 Options are defined in the root tool
		IOption[] options = rootTool.getOptions();
		assertEquals(4, options.length);
		// First option is a 2-element list
		assertEquals("List Option in Top", options[0].getName());
		assertEquals(IOption.STRING_LIST, options[0].getValueType());
		String[] valueList = options[0].getStringListValue();
		assertEquals(2, valueList.length);
		assertEquals("a", valueList[0]);
		assertEquals("b", valueList[1]);
		assertEquals(options[0].getCommand(), "-L");
		// Next option is a boolean in top
		assertEquals("Boolean Option in Top", options[1].getName());
		assertEquals(IOption.BOOLEAN, options[1].getValueType());
		assertEquals(false, options[1].getBooleanValue());
		assertEquals("-b", options[1].getCommand());
		// Next option is a string category
		assertEquals("String Option in Category", options[2].getName());
		assertEquals(IOption.STRING, options[2].getValueType());
		assertEquals("x", options[2].getStringValue());
		// Final option is an enumerated
		assertEquals("Enumerated Option in Category", options[3].getName());
		assertEquals(IOption.ENUMERATED, options[3].getValueType());
		assertEquals("Default Enum", options[3].getSelectedEnum());
		valueList = options[3].getApplicableValues();
		assertEquals(2, valueList.length);
		assertEquals("Default Enum", valueList[0]);
		assertEquals("Another Enum", valueList[1]);
		assertEquals("-e1", options[3].getEnumCommand(valueList[0]));
		assertEquals("-e2", options[3].getEnumCommand(valueList[1]));
		
		// Option Categories
		IOptionCategory topCategory = rootTool.getTopOptionCategory();
		assertEquals("Root Tool", topCategory.getName());
		options = topCategory.getOptions(null);
		assertEquals(2, options.length);
		assertEquals("List Option in Top", options[0].getName());
		assertEquals("Boolean Option in Top", options[1].getName());
		IOptionCategory[] categories = topCategory.getChildCategories();
		assertEquals(1, categories.length);
		assertEquals("Category", categories[0].getName());
		options = categories[0].getOptions(null);
		assertEquals(2, options.length);
		assertEquals("String Option in Category", options[0].getName());
		assertEquals("Enumerated Option in Category", options[1].getName());

		// Configs
		IConfiguration[] configs = target.getConfigurations();
		// Root Config
		IConfiguration rootConfig = configs[0];
		assertEquals("Root Config", rootConfig.getName());
		// Tools
		tools = rootConfig.getTools();
		assertEquals(1, tools.length);
		assertEquals("Root Tool", tools[0].getName());
		assertEquals("-r", tools[0].getOutputFlag());
		assertTrue(tools[0].buildsFileType("foo"));
		assertTrue(tools[0].buildsFileType("bar"));
		assertTrue(tools[0].producesFileType("toor"));
		assertEquals("doIt", tools[0].getToolCommand());
		assertEquals("", tools[0].getOutputPrefix());

		// Root Override Config
		assertEquals("Root Override Config", configs[1].getName());
		tools = configs[1].getTools();
		assertEquals(1, tools.length);
		assertTrue(tools[0] instanceof ToolReference);
		assertEquals("Root Tool", tools[0].getName());
		topCategory = tools[0].getTopOptionCategory();
		options = topCategory.getOptions(configs[1]);
		assertEquals(2, options.length);
		assertEquals("List Option in Top", options[0].getName());
		valueList = options[0].getStringListValue();
		assertEquals("a", valueList[0]);
		assertEquals("b", valueList[1]);
		assertEquals("Boolean Option in Top", options[1].getName());
		categories = topCategory.getChildCategories();
		options = categories[0].getOptions(configs[1]);
		assertEquals(2, options.length);
		assertEquals("String Option in Category", options[0].getName());
		assertEquals("y", options[0].getStringValue());
		assertEquals("Enumerated Option in Category", options[1].getName());
		valueList = options[1].getApplicableValues();
		assertEquals(2, valueList.length);
		assertEquals("Default Enum", valueList[0]);
		assertEquals("Another Enum", valueList[1]);
		assertEquals("-e1", options[1].getEnumCommand(valueList[0]));
		assertEquals("-e2", options[1].getEnumCommand(valueList[1]));
		assertEquals(1, tools.length);
		assertEquals("Root Tool", tools[0].getName());
		assertEquals("-r", tools[0].getOutputFlag());
		assertTrue(tools[0].buildsFileType("foo"));
		assertTrue(tools[0].buildsFileType("bar"));
		assertTrue(tools[0].producesFileType("toor"));
		assertEquals("doIt", tools[0].getToolCommand());
	}

	/*
	 * Do a sanity check on the values in the sub-target. Most of the
	 * sanity on the how build model entries are read is performed in 
	 * the root target check, so these tests just verify that the the sub 
	 * target properly inherits from its parent. For the new options
	 * in the sub target, the test does a sanity check just to be complete.
	 */
	private void checkSubTarget(ITarget target) throws BuildException {
		// Make sure this is a test target
		assertTrue(target.isTestTarget());
		// Make sure the build artifact extension is there
		assertEquals(target.getDefaultExtension(), subExt);
				
		// Get the tools for this target
		ITool[] tools = target.getTools();
		// Do we inherit properly from parent
		ITool rootTool = tools[0];
		assertEquals("Root Tool", rootTool.getName());
		// Now get the tool defined for this target
		ITool subTool = tools[1];
		assertEquals("Sub Tool", subTool.getName());
		// Confirm that it has three options
		IOption[] subOpts = subTool.getOptions();
		assertEquals(3, subOpts.length);
		assertEquals("", subTool.getOutputFlag());
		assertTrue(subTool.buildsFileType("yarf"));
		assertTrue(subTool.producesFileType("bus"));
		assertEquals("", subTool.getToolCommand());
		assertEquals("lib", subTool.getOutputPrefix());

		// Do a sanity check on the options 
		assertEquals("Include Paths", subOpts[0].getName());
		assertEquals(IOption.INCLUDE_PATH, subOpts[0].getValueType());
		String[] incPath = subOpts[0].getIncludePaths();
		assertEquals(2, incPath.length);
		assertEquals("/usr/include", incPath[0]);
		assertEquals("/opt/gnome/include", incPath[1]);
		assertEquals("-I", subOpts[0].getCommand());
		assertEquals("Defined Symbols", subOpts[1].getName());
		assertEquals(IOption.PREPROCESSOR_SYMBOLS, subOpts[1].getValueType());
		String[] defdSymbols = subOpts[1].getDefinedSymbols();
		assertEquals(0, defdSymbols.length);
		assertEquals("-D", subOpts[1].getCommand());
		assertEquals("More Includes", subOpts[2].getName());
		assertEquals(IOption.INCLUDE_PATH, subOpts[2].getValueType());
		String[] moreIncPath = subOpts[2].getIncludePaths();
		assertEquals(1, moreIncPath.length);
		assertEquals("/home/tester/include", moreIncPath[0]);
		assertEquals("-I", subOpts[2].getCommand());

		// Get the configs for this target
		IConfiguration[] configs = target.getConfigurations();
		// Check inheritance
		IConfiguration rootConfig = configs[0];
		assertEquals("Root Config", rootConfig.getName());
		assertEquals("Root Override Config", configs[1].getName());
		// Check the defined config for target
		IConfiguration subConfig = configs[2];
		assertEquals("Sub Config", subConfig.getName());
	}

	/**
	 * Remove all the project information associated with the project used during test.
	 */
	public void cleanup() {
		removeProject(projectName);
	}
	
	/**
	 * Create a new project named <code>name</code> or return the project in 
	 * the workspace of the same name if it exists.
	 * 
	 * @param name The name of the project to create or retrieve.
	 * @return 
	 * @throws CoreException
	 */
	private IProject createProject(String name) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(name);
		if (!project.exists()) {
			project.create(null);
		} else {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		}
        
		if (!project.isOpen()) {
			project.open(null);
		}
		
		//CCorePlugin.getDefault().convertProjectToC(project, null, CCorePlugin.PLUGIN_ID + ".make", true);

		return project;	
	}
	
	/**
	 * Remove the <code>IProject</code> with the name specified in the argument from the 
	 * receiver's workspace.
	 *  
	 * @param name
	 */
	private void removeProject(String name) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(name);
		if (project.exists()) {
			try {
				project.delete(true, false, null);
			} catch (CoreException e) {
				assertTrue(false);
			}
		}
	}
	
	/**
	 * Test that the build artifact of a <code>ITarget</code> can be modified
	 * programmatically.
	 */
	public void testTargetArtifacts () throws CoreException {
		// Open the test project
		IProject project = createProject(projectName);
		
		// Make sure there is one and only one target
		ITarget[] definedTargets = ManagedBuildManager.getTargets(project);
		assertEquals(1, definedTargets.length);
		ITarget rootTarget = definedTargets[0];
		
		// Set the build artifact of the target
		String ext = rootTarget.getDefaultExtension();
		String name = project.getName() + "." + ext;
		rootTarget.setBuildArtifact(name);
		
		// Save, close, reopen and test again
		ManagedBuildManager.saveBuildInfo(project);
		project.close(null);
		ManagedBuildManager.removeBuildInfo(project);
		project.open(null);

		// Make sure there is one and only one target
		definedTargets = ManagedBuildManager.getTargets(project);
		assertEquals(1, definedTargets.length);
		rootTarget = definedTargets[0];
		assertEquals(name, rootTarget.getArtifactName());
	}

	public void testThatAlwaysFails() {
		assertTrue(false);
	}
	
}
