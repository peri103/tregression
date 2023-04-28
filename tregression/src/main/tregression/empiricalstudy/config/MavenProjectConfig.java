package tregression.empiricalstudy.config;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;


import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;


import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
//import org.eclipse.aether.artifact.DefaultArtifact;
//import org.eclipse.aether.collection.CollectRequest;
//import org.eclipse.aether.graph.DependencyNode;
//import org.eclipse.aether.resolution.DependencyRequest;
//import org.eclipse.aether.repository.LocalRepository;
//import org.eclipse.aether.repository.RemoteRepository;
//import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.DefaultRepositorySystemSession;

//import org.eclipse.aether.artifact.Artifact;





public class MavenProjectConfig extends ProjectConfig {

	public final static String M2AFFIX = ".m2" + File.separator + "repository";

	public MavenProjectConfig(String srcTestFolder, String srcSourceFolder, String bytecodeTestFolder,
			String bytecodeSourceFolder, String buildFolder, String projectName, String regressionID) {
		super(srcTestFolder, srcSourceFolder, bytecodeTestFolder, bytecodeSourceFolder, buildFolder, projectName,
				regressionID);
	}

	public static boolean check(String path) {

		File f = new File(path);
		if (f.exists() && f.isDirectory()) {
			for (String file : f.list()) {
				if (file.toString().equals("pom.xml")) {
					return true;
				}
			}
		}

		return false;
	}


	public static ProjectConfig getConfig(String projectName, String regressionID) {
		return new MavenProjectConfig("src"+File.separator+"test"+File.separator+"java", 
				"src"+File.separator+"main"+File.separator+"java", 
				"target"+File.separator+"test-classes", 
				"target"+File.separator+"classes", 
				"target", 
				projectName, 
				regressionID);
	}
	
	public static List<String> getMavenDependencies(String path){
		String pomPath = path + File.separator + "pom.xml";
		File pomFile = new File(pomPath);

		try {
			List<String> dependencies = readAllDependency(pomFile);
			
			return dependencies;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return new ArrayList<String>();
	}

//	@SuppressWarnings("unchecked")
//	public static List<String> readAllDependency(File pom) throws Exception {
//		MavenXpp3Reader mavenReader = new MavenXpp3Reader();
//		Model pomModel = mavenReader.read(new FileReader(pom));
//		List<Dependency> dependencies = pomModel.getDependencies();
//		List<String> result = new ArrayList<>();
//		String usrHomePath = getUserHomePath();
//		for (Dependency dependency : dependencies) {
//			StringBuilder sb = new StringBuilder(usrHomePath);
//			sb.append(File.separator).append(M2AFFIX).append(File.separator)
//					.append(dependency.getGroupId().replace(".", File.separator)).append(File.separator)
//					.append(dependency.getArtifactId()).append(File.separator).append(dependency.getVersion())
//					.append(File.separator).append(dependency.getArtifactId()).append("-")
//					.append(dependency.getVersion()).append(".").append(dependency.getType());
//			result.add(sb.toString());
//		}
//		return result;
//	}
	

	public static List<String> readAllDependency(File pom) throws Exception {
	    MavenXpp3Reader mavenReader = new MavenXpp3Reader();
	    Model pomModel = mavenReader.read(new FileReader(pom));

	    RepositorySystem repoSystem = newRepositorySystem();
	    RepositorySystemSession session = newRepositorySystemSession(repoSystem);

	    org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(
	        pomModel.getGroupId(),
	        pomModel.getArtifactId(),
	        pomModel.getPackaging(),
	        pomModel.getVersion()
	    );

	    CollectRequest collectRequest = new CollectRequest(
	        new org.eclipse.aether.graph.Dependency(aetherArtifact, "compile"),
	        newRepositories(repoSystem, session)
	    );
	    DependencyNode node = repoSystem.collectDependencies(session, collectRequest).getRoot();
	    DependencyRequest dependencyRequest = new DependencyRequest(node, null);

	    repoSystem.resolveDependencies(session, dependencyRequest);

	    PreorderNodeListGenerator nodeListGenerator = new PreorderNodeListGenerator();
	    node.accept(nodeListGenerator);
	    List<org.eclipse.aether.artifact.Artifact> artifacts = nodeListGenerator.getArtifacts(false);

	    List<String> result = new ArrayList<>();
	    String usrHomePath = getUserHomePath();

	    for (org.eclipse.aether.artifact.Artifact resolvedArtifact : artifacts) {
	        String filePath = Paths.get(usrHomePath, ".m2", "repository", 
	        		resolvedArtifact.getGroupId().replace(".", File.separator), resolvedArtifact.getArtifactId(), 
	        		resolvedArtifact.getVersion(), resolvedArtifact.getArtifactId() + "-" + 
	        		resolvedArtifact.getVersion() + "." + resolvedArtifact.getExtension()).toString();

	        result.add(filePath);
	    }

	    return result;
	}

	private static RepositorySystem newRepositorySystem() {
	    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
	    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
	    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
	    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

	    return locator.getService(RepositorySystem.class);
	}



	private static RepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(System.getProperty("user.home") + "/.m2/repository");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
        return Arrays.asList(newCentralRepository());
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();
    }
	


	private static String getUserHomePath() {
		return SystemUtils.getUserHome().toString();
	}

}
