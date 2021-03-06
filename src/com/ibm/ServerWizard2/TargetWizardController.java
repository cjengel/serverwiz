package com.ibm.ServerWizard2;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.TreeItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class TargetWizardController implements PropertyChangeListener {
	SystemModel model;
	MainDialog view;
	LibraryManager xmlLib = new LibraryManager();
	private final String PROCESSING_SCRIPT="scripts/processMrw.pl";

	public TargetWizardController() {
	}

	public void init() {
		xmlLib.init();
		try {
			//xmlLib.update(version);
			xmlLib.loadModel(model);
			this.initModel();
		} catch (Exception e) {
			ServerWizard2.LOGGER.severe(e.toString());
			MessageDialog.openError(null, "Error",e.toString());
			e.printStackTrace();
			System.exit(4);
		}
	}
	public void initModel() throws Exception {
		model.deleteAllInstances();
		model.addUnitInstances();

		String parentTargetName = "sys-sys-power8";
		Target parentTarget = model.getTargetModels().get(parentTargetName);
		if (parentTarget == null) {
			throw new Exception("Parent model " + parentTargetName
					+ " is not valid");
		}
		// Create root instance
		Target sys = new Target(parentTarget);
		sys.setPosition(0);
		this.addTargetInstance(sys, null, null, "");
		//model.addTarget(null, sys);
	}

	public Target getTargetModel(String type) {
		return model.getTargetModel(type);
	}
	public void setView(MainDialog view) {
		this.view = view;
	}

	public void setModel(SystemModel model) {
		this.model = model;
	}

	public Vector<String> getEnums(String e) {
		if (model.enumerations.get(e)==null) {
			ServerWizard2.LOGGER.severe("Enum not found: "+e);
			return null;
		}
		return model.enumerations.get(e).enumList;
	}
	public Boolean isEnum(String e) {
		if (model.enumerations.get(e)==null) {
			return false;
		}
		return true;
	}


	public void deleteTarget(Target target) {
		//model.deleteTarget(target, model.rootTarget);
		model.deleteTarget(target);
	}

	public void addTargetInstance(Target targetModel, Target parentTarget,
			TreeItem parentItem,String nameOverride) {

		Target targetInstance;
		Target instanceCheck = model.getTargetInstance(targetModel.getType());
		if (instanceCheck!=null) {
			//target instance found of this model type
			targetInstance = new Target(instanceCheck);
			targetInstance.copyChildren(instanceCheck);
		} else {
			targetInstance = new Target(targetModel);
		}
		targetInstance.setName(nameOverride);
		model.updateTargetPosition(targetInstance, parentTarget, -1);
		try {
			model.addTarget(parentTarget, targetInstance);
			view.updateInstanceTree(targetInstance, parentItem);
		} catch (Exception e) {
			MessageDialog.openError(null, "Add Target Error", e.getMessage());
		}
	}
	public Target copyTargetInstance(Target target, Target parentTarget,Boolean incrementPosition) {
		Target newTarget = new Target(target);
		if (incrementPosition) {
			newTarget.setPosition(newTarget.getPosition()+1);
			newTarget.setSpecialAttributes();
		}
		try {
			model.addTarget(parentTarget, newTarget);
			newTarget.copyChildren(target);
		} catch (Exception e) {
			MessageDialog.openError(null, "Add Target Error", e.getMessage());
			newTarget=null;
		}
		return newTarget;
	}
	public void deleteConnection(Target target,Target busTarget,Connection conn) {
		target.deleteConnection(busTarget,conn);
	}
	public Vector<Target> getRootTargets() {
		return model.rootTargets;
	}

	public void writeXML(String filename) {
		try {
			String tmpFilename=filename+".tmp";
			model.writeXML(tmpFilename);
			File from = new File(tmpFilename);
			File to = new File(filename);
			Files.copy( from.toPath(), to.toPath(),StandardCopyOption.REPLACE_EXISTING );
			Files.delete(from.toPath());
			ServerWizard2.LOGGER.info(filename + " Saved");
		} catch (Exception exc) {
			MessageDialog.openError(null, "Error", exc.getMessage());
			exc.printStackTrace();
		}
	}

	public void readXML(String filename) {
		try {
			model.readXML(filename);
		} catch (Exception e) {
			MessageDialog.openError(null, "Error", e.getMessage());
			e.printStackTrace();
		}
	}
	public Vector<Target> getConnectionCapableTargets() {
		return model.getConnectionCapableTargets();
	}
	public void setGlobalSetting(String path,String attribute,String value) {
		model.setGlobalSetting(path, attribute, value);
	}
	public Field getGlobalSetting(String path,String attribute) {
		return model.getGlobalSetting(path, attribute);
	}
	public TreeMap<String,Field> getGlobalSettings(String path) {
		return model.getGlobalSettings(path);
	}
	public Vector<Target> getChildTargets(Target target) {
		//if (target.instanceModel) {
		//	return model.getChildTargetTypes("");
		//}
		return model.getChildTargetTypes(target.getType());
	}

	public void hideBusses(Target target) {
		target.hideBusses(model.getTargetLookup());
	}
	public Vector<Target> getVisibleChildren(Target target,boolean showHidden) {
		Vector<Target> children = new Vector<Target>();
		for (String c : target.getChildren()) {
			Target t = model.getTarget(c);
			if (t==null) {
				String msg="Invalid Child target id: "+c;
				ServerWizard2.LOGGER.severe(msg);
			}
			children.add(t);
		}
		if (showHidden) {
			for (String c : target.getHiddenChildren()) {
				Target t = model.getTarget(c);
				if (t==null) {
					String msg="Invalid Child target id: "+c;
					ServerWizard2.LOGGER.severe(msg);
				}
				children.add(t);
			}
		}
		return children;
	}

	public void initBusses(Target target) {
		model.initBusses(target);
	}
	public Vector<Target> getBusTypes() {
		return model.getBusTypes();
	}
	public void runChecks(String filename) {
		String commandLine[] = {
				"perl",
				"-I",
				LibraryFile.getWorkingDir(),
				LibraryFile.getWorkingDir()+PROCESSING_SCRIPT,
				"-x",
				filename,
				"-f"
		};
		String commandLineStr="";
		for (int i=0;i<commandLine.length;i++) {
			commandLineStr=commandLineStr+commandLine[i]+" ";
		}
		ServerWizard2.LOGGER.info("Running: "+commandLineStr);
		try {
			final ProcessBuilder builder = new ProcessBuilder(commandLine).redirectErrorStream(true);

			final Process process = builder.start();
			final StringWriter writer = new StringWriter();

			new Thread(new Runnable() {
			public void run() {
				char[] buffer = new char[1024];
				int len;
				InputStreamReader in = new InputStreamReader(process.getInputStream());

				try {
					while ((len = in.read(buffer)) != -1) {
					    writer.write(buffer, 0, len);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			}).start();

			final int exitValue = process.waitFor();
			final String processOutput = writer.toString();
			ServerWizard2.LOGGER.info(processOutput);
			ServerWizard2.LOGGER.info("Return Code: "+exitValue);
			LogViewerDialog dlg = new LogViewerDialog(null);
			dlg.setData(processOutput);
			dlg.open();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void propertyChange(PropertyChangeEvent arg0) {
		//view.setDirtyState(true);
	}
}