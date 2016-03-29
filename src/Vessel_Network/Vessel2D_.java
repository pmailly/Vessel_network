package Vessel_Network;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.File;
import ij.io.FileSaver;
import ij.measure.ResultsTable;
import ij.process.AutoThresholder;
import ij.process.ImageConverter;
import java.awt.Color;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import Skeletonize3D_.Skeletonize3D_;
import ij.gui.WaitForUserDialog;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import mcib3d.image3d.ImageByte;
import mcib3d.image3d.ImageHandler;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.SkeletonResult;



/**
 * @author Philippe Mailly
 * @created 15 06 2015
 */

public class Vessel2D_ implements PlugIn {

    private final int minArea = 300;     // Minimal vessel volume; pass to 3D objects counter 
    private double vesselArea; 
    private Calibration  cal = new Calibration();
    private String outDir;
    private List<Point> skelPt;
    private ImagePlus imgMap;
    

   
    private double[] meanDiameter(ImagePlus img) {
        double meanRad = 0;
        double rad;
        double maxRad = 0, minRad = Double.POSITIVE_INFINITY;
        double [] stats = {0,0,0};
        for (Point skelPt1 : skelPt) {
            rad = img.getProcessor().getPixelValue(skelPt1.x, skelPt1.y);
            //img.getProcessor().drawDot(skelPt.get(i).x, skelPt.get(i).y);
            //img.updateAndDraw();
            if (rad > maxRad) maxRad = rad;
            if (rad < minRad) minRad = rad;
            meanRad += rad;
        }
        stats[0] = meanRad/skelPt.size();
        stats[1] = minRad;
        stats[2] = maxRad;
        //FileSaver imgPt_save = new FileSaver(img);
        //imgPt_save.saveAsTiff(outDir+img.getTitle()+"_Pt.tif");
        return(stats);
    }
    
     // Calculate lenght of branches after skeletonize
    // Write final results
    public void  analyzeSkeleton (ImagePlus img, double vesselArea, BufferedWriter output, String fileNameNoExt) {
	int nbSkeleton;             // number of skeleton
        float totalLength = 0;     // total branch lenght/spheroid
        int totalBranches = 0;   // total number of branches/spheroid
        float imageArea = (float)(img.getWidth()*cal.pixelWidth * img.getHeight()*cal.pixelHeight);
        double pourcentVessel = (vesselArea/imageArea)*100;
        double[] stats;
        double meanD, maxD, minD;
        
        AnalyzeSkeleton_ analyzeSkeleton = new AnalyzeSkeleton_();
        AnalyzeSkeleton_.displaySkeletons  = true;
        analyzeSkeleton.setup("",img);
        SkeletonResult skeletonResults = analyzeSkeleton.run(AnalyzeSkeleton_.NONE,false,true,img,true,false);
        nbSkeleton = skeletonResults.getNumOfTrees();
        skelPt = skeletonResults.getListOfSlabVoxels();
        stats = meanDiameter(imgMap);
        meanD = stats[0]*2;
        minD = stats[1]*2;
        maxD = stats[2]*2;
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
        // save labelled skeletons
        ImageStack labSkel = analyzeSkeleton.getLabeledSkeletons();
        ImagePlus imgLab = new ImagePlus("Labelled skeleton",labSkel);
        IJ.run(imgLab,"Fire","");
        FileSaver imgLab_save = new FileSaver(imgLab);
        imgLab_save.saveAsTiff(outDir+fileNameNoExt+"_labSkel.tif");
        try {
            // write data
            output.write(fileNameNoExt + "\t" + vesselArea + "\t" + pourcentVessel + "\t"+ nbSkeleton + "\t" + totalBranches + "\t" + totalLength + "\t" + meanD + "\t" + minD + "\t" + maxD + "\n");
            output.flush();
        } catch (IOException ex) {
            Logger.getLogger(Vessel2D_.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    // remove smaller vessels
    private ImagePlus removeSmallVessels(ImagePlus img) {
        vesselArea = 0;
        IJ.run("Set Measurements...", "area redirect=None decimal=2");
        IJ.run(img,"Analyze Particles...", "size="+minArea+"-Infinity circularity=0.00-0.5 show=Masks clear");
        ResultsTable table = ResultsTable.getResultsTable();
        for (int i = 0; i < table.getCounter(); i++) {
            vesselArea += table.getValue("Area", i);
        }
        ImagePlus imgMask = WindowManager.getImage("Mask of "+img.getTitle());
        img.changes = false;
        img.close();
        return(imgMask);
    }
   
   
    
    
    public void run(String arg0) {
        try {
            String imageDir = IJ.getDirectory("Choose directory containing CZI files...");
            outDir = IJ.getDirectory("Choose Directory for output results files...");
            if ((imageDir == null) || (outDir == null)) return;
            FileWriter resultsFile;
            resultsFile = new FileWriter(outDir + "Vessel_results.xls", false);
            BufferedWriter outputResults = new BufferedWriter(resultsFile);
            
        
            // get the image files
            File inDir = new File(imageDir);
            String [] imageFile = inDir.list();
            int index = 0;
            if (imageFile == null) return;
            for (int i = 0; i < imageFile.length; i++) {
                if (imageFile[i].endsWith(".tif")) {
                    index++;
                    String fileNameWithOutExt = imageFile[i].substring(0, imageFile[i].indexOf(".tif"));
                    String imagePath = imageDir + imageFile[i];
                    IJ.run("Bio-Formats Importer", "open=["+imagePath+"] autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
                    ImagePlus imgOrg = WindowManager.getCurrentImage();
                    cal = imgOrg.getCalibration();
                    imgOrg.hide();
                    // write headers
                    if (index == 1) {    
                        outputResults.write("Image\tVessel Area ("+cal.getUnit()+"^2)\t% Vessel Area\t#Vessel\tTotal branches\tTotal Length ("+cal.getUnit()+")\tMean Diameter ("+cal.getUnit()+")\tMinD\tMaxD\n");
                        outputResults.flush();
                    }

                    // median and gaussian filters 
                    RankFilters medFilter = new RankFilters();
                    medFilter.rank(imgOrg.getProcessor(), 2, RankFilters.MEDIAN);
                    GaussianBlur gausFilter = new GaussianBlur();
                    gausFilter.blurGaussian(imgOrg.getProcessor(), 4);
                    
                    //  Substract background
                    IJ.run(imgOrg,"Subtract Background...", "rolling=25");
                    //  Threshold and binarize
                    imgOrg.getProcessor().setAutoThreshold(AutoThresholder.Method.Otsu, true);
                    
                    //new WaitForUserDialog("Image bin").show();

                    ImagePlus imgSkel = removeSmallVessels(imgOrg);
                    FileSaver imgSkel_save = new FileSaver(imgSkel);
                    imgSkel_save.saveAsTiff(outDir+"Mask of "+fileNameWithOutExt+".tif");
                    imgSkel.hide();
                    // Distance map
                    IJ.run(imgSkel,"Local Thickness (complete process)", "threshold=128");
                    // wait end of local thickness process
                    while (WindowManager.getImage("Mask of "+fileNameWithOutExt+"_LocThk") == null) {
                        IJ.wait(100);
                    }
                    imgMap = WindowManager.getImage("Mask of "+fileNameWithOutExt+"_LocThk");
                    imgMap.hide();
                    
                    FileSaver imgMap_save = new FileSaver(imgMap);
                    imgMap_save.saveAsTiff(outDir+"Map of "+fileNameWithOutExt+".tif");
                    
                    // Skeletonize and analyze skeleton
                    Skeletonize3D_ skeleton = new Skeletonize3D_();
                    skeleton.setup("",imgSkel);
                    skeleton.run(imgSkel.getProcessor());
                    imgSkel.updateAndDraw();
                    imgSkel_save.saveAsTiff(outDir+"Skel of "+fileNameWithOutExt+".tif");

                    // Analyze skeleton and write info
                    analyzeSkeleton(imgSkel, vesselArea, outputResults, fileNameWithOutExt);
                    imgSkel.close();
                    imgSkel.flush();
                    imgMap.close();
                    imgMap.flush();
                }
                IJ.showStatus("Process done");
                try {
                    outputResults.close();
                } catch (IOException ex) {
                    Logger.getLogger(Vessel2D_.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Vessel2D_.class.getName()).log(Level.SEVERE, null, ex);
        }
        }
    }
