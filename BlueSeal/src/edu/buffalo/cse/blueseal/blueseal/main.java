package edu.buffalo.cse.blueseal.blueseal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.buffalo.cse.blueseal.BSFlow.CgTransformer;

import soot.Pack;
import soot.PackManager;
import soot.SceneTransformer;
import soot.Transform;

public class main {
    public final static Debug d = new Debug(Constants.debugOn);
    private static String inputLoc = null; // input apk path
    private static String aaptPath;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.print("Missing input apk path!");
            System.exit(1);
        }

        // check if the apk path is valid
        aaptPath = args[0];
        inputLoc = args[1];

        if (!(new File(inputLoc).exists())) {
            System.err.print("APK path is invalid!");
            System.exit(1);
        }
        if (!(new File(aaptPath).exists())) {
            System.err.print("AAPT path is invalid!");
            System.exit(1);
        }

        Constants.setAAPTpath(aaptPath);

        d.printOb("Welcome to BlueSeal!");

        GlobalData.setApkLocation(inputLoc);

        SceneTransformer transformer = new CgTransformer(inputLoc);
        

        SceneTransformer shimpleTransformer = new AnotherShimpleTransformer();
        //SceneTransformer shimpleTransformer = new BSShimpleTransformer();

//        PackManager.v().getPack("cg").add(new Transform("cg.Tran", transformer));
//
//        PackManager.v().getPack("wstp").add(new Transform("wstp.Tran", shimpleTransformer));
        Pack pack = PackManager.v().getPack("wstp");
        pack.add(new Transform("wstp.cg", transformer));

//        pack.add(new Transform("wstp.Tran", shimpleTransformer));

        //redirect the stdout to get rid of Soot output info
        try {
            System.setOut(new PrintStream("/dev/null"));
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        String[] sootArgs = { "-w", "-ws", "-f", "n", "-allow-phantom-refs", "-x",
                "android.support.", "-x", "android.annotation.", "-process-dir", inputLoc,
                "-android-jars", Constants.ANDROID_JARS, "-src-prec", "apk", "-ire" };
        soot.Main.main(sootArgs);

        /*
         * The following code in comments are for exra permission
         */
        /*
         * Debug.printOb(PackManager.v().allPacks().toString()); Set<SootMethod>
         * methods = ((BSSceneTransformer)transformer).getReachableMethods();
         * Debug.printSet("RM", methods); Set<String> sigs =
         * ((BSSceneTransformer)transformer).getReachableMethSigs();
         * MinPermissions minPerm = new MinPermissions(inputLoc, methods, sigs);
         * Set<String> extraPerms = minPerm.getExtraPerms();
         * Debug.printSet("Extra Permissions", extraPerms);
         */

        /*
         * the following code is for detecting contentprovider methods, may need
         * to remove later
         */
        /*
         * InfoSummary infoSum = new InfoSummary(); infoSum.getAllCPUnits();
         * Set<SootMethod> method = infoSum.getContentProviderMeth();
         * Debug.printSet("ContentProvidermethods", method);
         */

        // the following code is printing uris
        Debug.printSet("URIS~~~~", MethodAnalysis.getCPURIs());
    }
}
