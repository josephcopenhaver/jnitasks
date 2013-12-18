/* JNITasks: Ant tasks for JNI projects.
 * Copyright (C) 2013-2014 Alexander Barker.  All Rights Received.
 * https://github.com/kwhat/jnitasks/
 *
 * JNITasks is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JNITasks is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jnitasks;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.FileSet;
import org.jnitasks.toolchains.LinkerAdapter;
import org.jnitasks.toolchains.ToolchainFactory;
import org.jnitasks.types.AbstractFeature;

import java.io.File;
import java.util.Iterator;
import java.util.Vector;

public class LdTask extends MatchingTask {
	protected Vector<AbstractFeature> features = new Vector<AbstractFeature>();
	private String outfile = null;
	private String toolchain = "gcc";
	private String host = "";

	public void addFileset(FileSet fileset) {
		// Wrap FileSet to allow for argument order.
		LdTask.FileSetArgument arg = new LdTask.FileSetArgument();
		arg.setFileSet(fileset);

		features.add(arg);
	}

	public void addLibrary(Library library) {
		features.add(library);
	}

	public void setToolchain(String toolchain) {
		this.toolchain = toolchain;
	}

	public void setOutfile(String outfile) {
		this.outfile = outfile;
	}

	public LdTask.Argument createArg() {
		LdTask.Argument arg = new LdTask.Argument();
		features.add(arg);

		return arg;
	}

	public void execute() {
		if (outfile == null) {
			throw new BuildException("The outfile attribute is required");
		}

		// Setup the compiler.
		LinkerAdapter linker = ToolchainFactory.getLinker(toolchain);
		linker.setProject(getProject());
		linker.setOutFile(outfile);

		if (host.length() > 0) {
			// Prepend the host string to the executable.
			linker.setExecutable(host + '-' + linker.getExecutable());
		}
		else if (getProject().getProperty("ant.build.native.linker") != null) {
			linker.setExecutable(getProject().getProperty("ant.build.native.compiler"));
		}
		else if (System.getenv().get("CC") != null) {
			linker.setExecutable(System.getenv().get("LD"));
		}

		for (AbstractFeature feat : features) {
			if (feat.isValidOs() && feat.isIfConditionValid() && feat.isUnlessConditionValid()) {
				linker.addArg(feat);
			}
		}


		// Print the executed command.
		Echo echo = (Echo) getProject().createTask("echo");
		echo.setTaskName(this.getTaskName());
		echo.setAppend(true);

		// Create an exec task to run a shell.  Using the current shell to
		// execute commands is required for Windows support.
		ExecTask shell = (ExecTask) getProject().createTask("exec");
		shell.setTaskName(this.getTaskName());
		shell.setFailonerror(true);
		//shell.setDir(dir);

		echo.addText(linker.getExecutable());
		shell.setExecutable(linker.getExecutable());

		Iterator<String> args = linker.getArgs();
		while (args.hasNext()) {
			String arg = args.next();

			echo.addText(" " + arg);
			shell.createArg().setLine(arg);
		}

		echo.execute();
		shell.execute();
	}

	public static class Argument extends AbstractFeature {
		private String value;

		public void setValue(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	public static class Library extends AbstractFeature {
		private File path;
		private String lib;

		public void setPath(File path) {
			this.path = path;
		}

		public File getPath() {
			return this.path;
		}

		public void setLib(String lib) {
			this.lib = lib;
		}

		public String getLib() {
			return lib;
		}
	}

	public static class FileSetArgument extends AbstractFeature {
		private FileSet files;

		public void setFileSet(FileSet files) {
			this.files = files;
		}

		public FileSet getFileSet() {
			return this.files;
		}
	}
}
