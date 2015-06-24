package Vessel_Network;

import Skeletonize3D_.Skeletonize3D_;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.File;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.processing.FastFilters3D;
import features.TubenessProcessor;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.plugin.Duplicator;
import ij.process.AutoThresholder;
import ij.process.ImageConverter;
import ij.util.ArrayUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.Math.sqrt;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageByte;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageLabeller;
import skeleton_analysis.AnalyzeSkeleton_;
import skeleton_analysis.Edge;
import skeleton_analysis.SkeletonResult;


/**
 * @author Philippe Mailly
 * @created 15 06 2015
 */

public class Vessel3D_ implements PlugIn {

    static float tubeRadius;     // radius for tubeness processing
    static float vesselRadius;   // approximative vessel radius
    static int vesselThreshold;  // threshold for vessel -1 for manual; pass to 3D objects counter
    static int minVolume;     // Minimal vessel volume; pass to 3D objects counter 
    static boolean removeBranches;    // Remove branches with end-points; pass to skeletonize
    static int dilateSkeleton;         // Dilate Skeleton for viewing by (pixels)
    static Calibration  cal = new Calibration();

   /*
    Create dialog box for parameters
    */ 
   private boolean createDialog () {
       
       boolean  gdOk = true;
       String unit = cal.getUnit();
       GenericDialog gd = new GenericDialog("Vessel Analyze Parameters");
       gd.addNumericField("Tube Radius (" + unit +") :", 5, 4);
       gd.addNumericField("Vessel Radius (" + unit +") :", 5, 4);
       gd.addNumericField("Minimal Vessel Volume (" + unit +") :", 1000, 8);
       gd.addCheckbox("Remove branches with end-points", false);
       gd.addNumericField("Dilate Skeleton for viewing by (pixels)", 2, 2);
       gd.showDialog();
       if (gd.wasCanceled()) gdOk = false;
       tubeRadius = (int)gd.getNextNumber();
       vesselRadius = (int)gd.getNextNumber();
       minVolume = (int)gd.getNextNumber();
       removeBranches = gd.getNextBoolean();
       dilateSkeleton = (int)gd.getNextNumber();
       return gdOk;
   } 
   

    /*
    Get objects population
   */
    private Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        labels.setCalibration(img.getCalibration());
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }
    
     // Calculate lenght of branches after skeletonize
    // Write final results
    public void analyzeSkeleton (ImagePlus img, double vesselVolume, BufferedWriter output, String fileNameNoExt) {
	int nbSkeleton;             // number of skeleton
        double totalLength = 0;     // total branch lenght/spheroid
        int totalBranches = 0;   // total number of branches/spheroid
        double imageVolume = img.getWidth()*cal.pixelWidth * img.getHeight()*cal.pixelHeight * img.getNSlices()*cal.pixelDepth;
        IJ.log("Volume Image :"+imageVolume);
        double pourcentVessel = vesselVolume/imageVolume;
        
        AnalyzeSkeleton_ analyzeSkeleton = new AnalyzeSkeleton_();
        AnalyzeSkeleton_.displaySkeletons  = true;
        analyzeSkeleton.setup("",img);
        SkeletonResult skeletonResults = analyzeSkeleton.run(AnalyzeSkeleton_.NONE,removeBranches,true,null,true,true);
        nbSkeleton = skeletonResults.getNumOfTrees();
        int[] branches = skeletonResults.getBranches();
        for (int b = 0; b < branches.length; b++) { 
                totalBranches += branches[b];
        }
        for (int i = 0; i < nbSkeleton; i++) {
            ArrayList<Edge> listEdges;
            listEdges = skeletonResults.getGraph()[i].getEdges();
            for (int e = 0; e < listEdges.size(); e++) {
                totalLength += listEdges.get(e).getLength();
            }
        }
        try {
            // write data
            output.write(fileNameNoExt + "\t" + vesselVolume + "\t" + pourcentVessel + "\t"+ nbSkeleton + "\t" + totalBranches + "\t" + totalLength + "\n");
            output.flush();
        } catch (IOException ex) {
            Logger.getLogger(Vessel3D_.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    // threshold image and binarize
    private ImagePlus threshold(ImagePlus img) {
            new ImageConverter(img).convertToGray8();
            AutoThresholder at = new AutoThresholder();
            int th = at.getThreshold(AutoThresholder.Method.IsoData, img.getStatistics().histogram);
            ImageHandler ha = ImageHandler.wrap(img);
            ImageByte bin = ha.thresholdAboveExclusive(th);
            ImagePlus imgBin = bin.getImagePlus();
            imgBin.setCalibration(cal);
            imgBin.updateAndDraw();
            return(imgBin);
    }
    
    
    public void run(String arg0) {
        
        try {
            String imageDir = IJ.getDirectory("Choose directory containing TIF files...");
            String outDir = IJ.getDirectory("Choose Directory for output results files...");
            if ((imageDir == null) || (outDir == null)) return;
            
            // Write headers results
            FileWriter resultsFile;
            resultsFile = new FileWriter(outDir + "Vessel_results.xls", false);
            BufferedWriter outputResults = new BufferedWriter(resultsFile);
            outputResults.write("Image\tVessel Volume\t% Vessel Volume\t#Vessel\tTotal branches\tTotal Length\n");
            outputResults.flush();
        
            // get the image files
            File inDir = new File(imageDir);
            String [] imageFile = inDir.list();
            int imageIndex = 0;
            if (imageFile == null) return;
            for (int i = 0; i < imageFile.length; i++) {
                if (imageFile[i].endsWith(".tif")) {
                    String fileNameWithOutExt = imageFile[i].substring(0, imageFile[i].length() - 4);
                    imageIndex++;
                    String imagePath = imageDir + imageFile[i];
                    Opener imgOpener = new Opener();
                    ImagePlus imgOrg = imgOpener.openImage(imagePath);
                    cal = imgOrg.getCalibration();
                    if (imageIndex == 1) if (createDialog() == false) return; // Ask for parameters for the first image
                    
                    // Fast filters closing
                    ImageInt imgInt = ImageInt.wrap(imgOrg);
                    ImageInt intClose = FastFilters3D.filterIntImage(imgInt, FastFilters3D.CLOSEGRAY, tubeRadius, tubeRadius, (float)cal.pixelHeight, ij.util.ThreadUtil.getNbCpus(), true);
                    ImagePlus imgClose = intClose.getImagePlus();
                    imgClose.setCalibration(cal);
                    
                    // Run tubeness
                    TubenessProcessor tubeP = new TubenessProcessor(vesselRadius,true);
                    ImagePlus tubeImg= tubeP.generateImage(imgClose);
                    tubeImg.show();
                    new WaitForUserDialog("Image Tubeness").show();
                    imgClose.close();
                    imgClose.flush();
                    
                    //  Threshold tubeness and binarize
                    ImagePlus imgBin = threshold(tubeImg);
                    imgBin.show();
                    new WaitForUserDialog("Image bin").show();
                    
                    // Find 3D objects    
                    IJ.showStatus("Computing object population ...");
                    Objects3DPopulation popVessel = getPopFromImage(imgBin);
                    
                    // filter size keep only object > minVolume and label image
                    ImageInt imgLabel = ImageInt.wrap(imgOrg);
                    ImageHandler imgObjects = imgLabel.createSameDimensions();
                    imgObjects.set332RGBLut();
                    imgObjects.setCalibration(cal);
                    Random randomGenerator = new Random();
                    double totalVesselVolume = 0;
                    for (int n = 0; n < popVessel.getNbObjects(); n++) {
                        if (popVessel.getObject(n).getVolumeUnit() >= minVolume) {
                            popVessel.getObject(n).draw(imgObjects, randomGenerator.nextInt(255));
                            totalVesselVolume += totalVesselVolume + popVessel.getObject(n).getVolumeUnit();
                            IJ.log("Volume : "+popVessel.getObject(n).getVolumeUnit());
                        }
                    }
                    imgObjects.getImagePlus().updateAndDraw();
                    imgObjects.show();
                    FileSaver imgLabel_save = new FileSaver(imgObjects.getImagePlus());
                    imgLabel_save.saveAsTiffStack(outDir+fileNameWithOutExt+"_Label.tif");
                    new WaitForUserDialog("Image label").show();
                    
                    // Skeletonize and analyze skeleton
                    Duplicator imgDup = new Duplicator();
                    ImagePlus imgSkel = imgDup.run(imgObjects.getImagePlus(),1,imgBin.getNSlices());
                    ImagePlus imgSkelBin = threshold(imgSkel);
                    imgSkel.close();
                    imgSkel.flush();
                    Skeletonize3D_ skeleton = new Skeletonize3D_();
                    skeleton.setup("",imgSkelBin);
                    skeleton.run(imgSkelBin.getProcessor());
                    imgSkelBin.updateAndDraw();
                    imgSkelBin.show();
                    new WaitForUserDialog("Image Skeleton").show();
                   
                    // Analyze and write info
                    analyzeSkeleton(imgSkelBin, totalVesselVolume, outputResults, fileNameWithOutExt);
                    imgOrg.close();
                }
                
            }
            IJ.showStatus("Process done");
            outputResults.close();
        } catch (IOException ex) {
            Logger.getLogger(Vessel3D_.class.getName()).log(Level.SEVERE, null, ex);
        }
    }   
}
