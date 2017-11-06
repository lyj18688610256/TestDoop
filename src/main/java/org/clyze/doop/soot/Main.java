package org.clyze.doop.soot;

import org.clyze.doop.common.Database;
import org.clyze.doop.util.filter.GlobClassFilter;
import org.clyze.utils.AARUtils;

import org.objectweb.asm.ClassReader;
import soot.*;
import soot.SourceLocator.FoundFile;
import soot.options.Options;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.apache.commons.io.FileUtils;

import org.clyze.doop.soot.android.AndroidSupport;

public class Main {

    private static int shift(String[] args, int index) {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }
        return index + 1;
    }

    private static boolean isApplicationClass(SootParameters sootParameters, SootClass klass) {
        sootParameters.applicationClassFilter = new GlobClassFilter(sootParameters.appRegex);

        return sootParameters.applicationClassFilter.matches(klass.getName());
    }

    public static void main(String[] args) {
        SootParameters sootParameters = new SootParameters();
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                throw new DoopErrorCodeException(0);
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--full":
                        if (sootParameters._mode != null) {
                            System.err.println("error: duplicate mode argument");
                            throw new DoopErrorCodeException(1);
                        }
                        sootParameters._mode = SootParameters.Mode.FULL;
                        break;
                    case "-d":
                        i = shift(args, i);
                        sootParameters._outputDir = args[i];
                        break;
                    case "--main":
                        i = shift(args, i);
                        sootParameters._main = args[i];
                        break;
                    case "--ssa":
                        sootParameters._ssa = true;
                        break;
                    case "--android-jars":
                        i = shift(args, i);
                        sootParameters._allowPhantom = true;
                        sootParameters._android = true;
                        sootParameters._androidJars = args[i];
                        break;
                    case "-l":
                        i = shift(args, i);
                        sootParameters._libraries.add(args[i]);
                        break;
                    case "-lsystem":
                        String javaHome = System.getProperty("java.home");
                        sootParameters._libraries.add(javaHome + File.separator + "lib" + File.separator + "rt.jar");
                        sootParameters._libraries.add(javaHome + File.separator + "lib" + File.separator + "jce.jar");
                        sootParameters._libraries.add(javaHome + File.separator + "lib" + File.separator + "jsse.jar");
                        break;
                    case "--deps":
                        i = shift(args, i);
                        String folderName = args[i];
                        File f = new File(folderName);
                        if (!f.exists()) {
                            System.err.println("Dependency folder " + folderName + " does not exist");
                            throw new DoopErrorCodeException(0);
                        } else if (!f.isDirectory()) {
                            System.err.println("Dependency folder " + folderName + " is not a directory");
                            throw new DoopErrorCodeException(0);
                        }
                        for (File file : f.listFiles()) {
                            if (file.isFile() && file.getName().endsWith(".jar")) {
                                sootParameters._libraries.add(file.getCanonicalPath());
                            }
                        }
                        break;
                    case "--application-regex":
                        i = shift(args, i);
                        sootParameters.appRegex = args[i];
                        break;
                    case "--allow-phantom":
                        sootParameters._allowPhantom = true;
                        break;
                    case "--run-flowdroid":
                        sootParameters._runFlowdroid = true;
                        break;
                    case "--only-application-classes-fact-gen":
                        sootParameters._onlyApplicationClassesFactGen = true;
                        break;
                    case "--generate-jimple":
                        sootParameters._generateJimple = true;
                        break;
                    case "--stdout":
                        sootParameters._toStdout = true;
                        break;
                    case "--noFacts":
                        sootParameters._noFacts = true;
                        break;
                    case "--uniqueFacts":
                        sootParameters._uniqueFacts = true;
                        break;
                    case "--R-out-dir":
                        i = shift(args, i);
                        sootParameters._rOutDir = args[i];
                        break;
                    case "-h":
                    case "--help":
                    case "-help":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --main <class>                        Specify the main name of the main class");
                        System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
                        System.err.println("  --full                                Generate facts by full transitive resolution");
                        System.err.println("  -d <directory>                        Specify where to generate csv fact files");
                        System.err.println("  -l <archive>                          Find classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --deps <directory>                    Add jars in this directory to the class lookup path");
                        System.err.println("  --only-application-classes-fact-gen   Generate facts only for application classes");
                        System.err.println("  --noFacts                             Don't generate facts (just empty files -- used for debugging)");
                        System.err.println("  --uniqueFacts                         Eliminate redundancy from facts");
                        System.err.println("  --R-out-dir <directory>               Specify when to generate R code (when linking AAR inputs)");

                        System.err.println("  --generate-jimple                     Generate Jimple/Shimple files instead of facts");
                        System.err.println("  --generate-jimple-help                Show help information regarding bytecode2jimple");
                        throw new DoopErrorCodeException(0);
                    case "--generate-jimple-help":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --ssa                                 Generate Shimple files (use SSA for variables)");
                        System.err.println("  --full                                Generate Jimple/Shimple files by full transitive resolution");
                        System.err.println("  --stdout                              Write Jimple/Shimple to stdout");
                        System.err.println("  -d <directory>                        Specify where to generate files");
                        System.err.println("  -l <archive>                          Find classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --android-jars <archive>              The main android library jar (for android apks). The same jar should be provided in the -l option");
                        throw new DoopErrorCodeException(0);
                    default:
                        if (args[i].charAt(0) == '-') {
                            System.err.println("error: unrecognized option: " + args[i]);
                            throw new DoopErrorCodeException(0);
                        } else {
                            sootParameters._inputs.add(args[i]);
                        }
                        break;
                }
            }

            if(sootParameters._mode == null) {
                sootParameters._mode = SootParameters.Mode.INPUTS;
            }

            if (sootParameters._toStdout && !sootParameters._generateJimple) {
                System.err.println("error: --stdout must be used with --generate-jimple");
                throw new DoopErrorCodeException(1);
            }
            if (sootParameters._toStdout && sootParameters._outputDir != null) {
                System.err.println("error: --stdout and -d options are not compatible");
                throw new DoopErrorCodeException(2);
            }
            else if ((sootParameters._inputs.stream().filter(s -> s.endsWith(".apk") || s.endsWith(".aar")).count() > 0) &&
                    (!sootParameters._android)) {
                System.err.println("error: the --platform parameter is mandatory for .apk/.aar inputs");
                throw new DoopErrorCodeException(3);
            }
            else if (!sootParameters._toStdout && sootParameters._outputDir == null) {
                sootParameters._outputDir = System.getProperty("user.dir");
            }
            produceFacts(sootParameters);
        }
        catch(DoopErrorCodeException errCode) {
            int n = errCode.getErrorCode();
            if (n != 0)
                System.err.println("Exiting with code " + n);
        }
        catch(Exception exc) {
            exc.printStackTrace();
        }
    }

    private static void produceFacts(SootParameters sootParameters) throws Exception {
        Options.v().set_output_dir(sootParameters._outputDir);
        Options.v().setPhaseOption("jb", "use-original-names:true");

        if (sootParameters._ssa) {
            Options.v().set_via_shimple(true);
            Options.v().set_output_format(Options.output_format_shimple);
        } else {
            Options.v().set_output_format(Options.output_format_jimple);
        }
        //soot.options.Options.v().set_drop_bodies_after_load(true);
        Options.v().set_keep_line_number(true);

        PropertyProvider propertyProvider = new PropertyProvider();
        Set<SootClass> classes = new HashSet<>();
        Set<String> classesInApplicationJar = new HashSet<>();

        String input0 = sootParameters._inputs.get(0);
        AndroidSupport android = null;

        // Set of temporary directories to be cleaned up after analysis ends.
        Set<String> tmpDirs = new HashSet<>();
        if (sootParameters._android) {
            String rOutDir = sootParameters._rOutDir;
            android = new AndroidSupport(rOutDir, input0, sootParameters);
            android.processInputs(propertyProvider, classesInApplicationJar, sootParameters._androidJars, tmpDirs);
        } else {
            Options.v().set_src_prec(Options.src_prec_class);
            populateClassesInAppJar(input0, classesInApplicationJar, propertyProvider);
        }

        Scene scene = Scene.v();
        scene.setSootClassPath("");
        for (String input : sootParameters._inputs) {
            if (input.endsWith(".jar") || input.endsWith(".jar")) {
                System.out.println("Adding archive: " + input);
            }
            else {
                System.out.println("Adding file: " + input);
            }
            scene.extendSootClassPath(input);
        }

        for (String lib : AARUtils.toJars(sootParameters._libraries, false, tmpDirs)) {
            System.out.println("Adding archive for resolving: " + lib);
            scene.extendSootClassPath(lib);
        }

        if (sootParameters._main != null) {
            Options.v().set_main_class(sootParameters._main);
        }

        if (sootParameters._mode == SootParameters.Mode.FULL) {
            Options.v().set_full_resolver(true);
        }

        if (sootParameters._allowPhantom) {
            Options.v().set_allow_phantom_refs(true);
        }

        if (sootParameters._android) {
            if (android == null) {
                System.err.println("Internal error when adding Android classes.");
            } else {
                android.addClasses(classesInApplicationJar, classes, scene);
            }
        } else {
            addClasses(classesInApplicationJar, classes, scene);

            System.out.println("Classes in application jar: " + classesInApplicationJar.size());

            /*
             * Set resolution level for sun.net.www.protocol.ftp.FtpURLConnection
             * to 1 (HIERARCHY) before calling produceFacts(). The following line is necessary to avoid
             * a runtime exception when running soot with java 1.8, however it leads to different
             * input fact generation thus leading to different analysis results
             */
            scene.addBasicClass("sun.net.www.protocol.ftp.FtpURLConnection", 1);
            /*
             * For simulating the FileSystem class, we need the implementation
             * of the FileSystem, but the classes are not loaded automatically
             * due to the indirection via native code.
             */
            addCommonDynamicClass(scene, "java.io.UnixFileSystem");
            addCommonDynamicClass(scene, "java.io.WinNTFileSystem");
            addCommonDynamicClass(scene, "java.io.Win32FileSystem");

            /* java.net.URL loads handlers dynamically */
            addCommonDynamicClass(scene, "sun.net.www.protocol.file.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.ftp.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.http.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.https.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.jar.Handler");
        }

        scene.loadNecessaryClasses();
        
        /*
        * This part should definitely appear after the call to
        * `Scene.loadNecessaryClasses()', since the latter may alter
        * the set of application classes by explicitly specifying
        * that some classes are library code (ignoring any previous
        * call to `setApplicationClass()').
        */

        classes.stream().filter((klass) -> isApplicationClass(sootParameters, klass)).forEachOrdered(SootClass::setApplicationClass);

        if (sootParameters._mode == SootParameters.Mode.FULL && !sootParameters._onlyApplicationClassesFactGen) {
            classes = new HashSet<>(scene.getClasses());
        }

        System.out.println("Total classes in Scene: " + classes.size());
        try {
            PackManager.v().retrieveAllSceneClassesBodies();
            System.out.println("Retrieved all bodies");
        }
        catch (Exception ex) {
            System.out.println("Not all bodies retrieved");
        }
        Database db = new Database(new File(sootParameters._outputDir), sootParameters._uniqueFacts);
        FactWriter writer = new FactWriter(db);
        ThreadFactory factory = new ThreadFactory(writer, sootParameters._ssa, sootParameters._generateJimple);
        Driver driver = new Driver(factory, classes.size(), sootParameters._generateJimple);

        classes.stream().filter(SootClass::isApplicationClass).forEachOrdered(writer::writeApplicationClass);

        // Read all stored properties files
        for (Map.Entry<String, Properties> entry : propertyProvider.getProperties().entrySet()) {
            String path = entry.getKey();
            Properties properties = entry.getValue();

            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                writer.writeProperty(path, propertyName, propertyValue);
            }
        }

        db.flush();

        if (sootParameters._android) {
            if (android == null) {
                System.err.println("Internal error in Android handling.");
            } else {
                if (sootParameters._runFlowdroid) {
                    driver.doAndroidInSequentialOrder(android.getDummyMain(), classes, writer, sootParameters._ssa);
                    db.close();
                    return;
                } else {
                    android.writeComponents(writer);
                }
            }
        }
        if (!sootParameters._noFacts) {

            scene.getOrMakeFastHierarchy();
            // avoids a concurrent modification exception, since we may
            // later be asking soot to add phantom classes to the scene's hierarchy
            driver.doInParallel(classes);
        }

        db.close();

        // Clean up any temporary directories used for AAR extraction.
        for (String tmpDir : tmpDirs) {
            FileUtils.deleteQuietly(new File(tmpDir));
        }

    }

    private static void addCommonDynamicClass(Scene scene, String className) {
        if( SourceLocator.v().getClassSource(className) != null) {
            scene.addBasicClass(className);
        }
    }

    /**
     * Helper method to read classes and property files from JAR/AAR files.
     *
     * @param jarFileName              the name of the JAR to read
     * @param classesInApplicationJAR  the set to populate
     * @param propertyProvider         the provider to use for .properties files
     * @param androidMode              if set, process classes.jar in .aar files
     *
     * @return the name of the JAR file that was processed; this is
     * either the original first parameter, or the locally saved
     * classes.jar found in the .aar file (if such a file was given)
     *
     */
    public static String populateClassesInAppJar(String jarFileName,
                                                 Set<String> classesInApplicationJar,
                                                 PropertyProvider propertyProvider) throws Exception {
        JarEntry entry;

        System.out.println("Processing application JAR: " + jarFileName);
        try (JarInputStream jin = new JarInputStream(new FileInputStream(jarFileName));
             JarFile jarFile = new JarFile(jarFileName)) {

            /* List all JAR entries */
            while ((entry = jin.getNextJarEntry()) != null) {
                /* Skip directories */
                if (entry.isDirectory())
                    continue;

                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    ClassReader reader = new ClassReader(jarFile.getInputStream(entry));
                    classesInApplicationJar.add(reader.getClassName().replace("/", "."));
                } else if (entryName.endsWith(".properties")) {
                    propertyProvider.addProperties((new FoundFile(jarFileName, entryName)));
                } else {
                    /* Skip non-class files and non-property files */
                    continue;
                }
            }
            return jarFileName;
        }
    }

    public static void addClasses(Set<String> classesInApplicationJar, Set<SootClass> classes, Scene scene) {
        for (String className : classesInApplicationJar) {
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            classes.add(c);
        }
    }
}
