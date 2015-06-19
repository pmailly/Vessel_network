package Vessel_Network;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.File;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.processing.FastFilters3D;
import mcib3d.utils.ThreadUtil;
import features.TubenessProcessor;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageByte;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageLabeller;


/**
 * @author Philippe Mailly
 * @created 15 06 2015
 */

public class Vessel3D_ implements PlugIn {

    static float tubeRadius;     // radius for tubeness processing
    static float vesselRadius;   // approximative vessel radius
    static int vesselThreshold;  // threshold for vessel -1 for manual; pass to 3D objects counter
    static int minVolume;     // Minimal vessel volume in pixels; pass to 3D objects counter 
    static boolean removeBranches;    // Remove branches with end-points; pass to skeletonize
    static int dilateSkeleton;         // Dilate Skeleton for viewing by (pixels)
    static Calibration  cal = new Calibration();

    
   private boolean createDialog () {
       
       boolean  gdOk = true;
       String unit = cal.getUnit();
       GenericDialog gd = new GenericDialog("Vessel Analyze Parameters");
       gd.addNumericField("Tube Radius" + unit +" :", 5, 4);
       gd.addNumericField("Vessel Radius" + unit +" :", 5, 4);
       gd.addNumericField("Vessel Threshold :", 8, 3);
       gd.addNumericField("Minimal Vessel Volume" + unit +" :", 1000, 8);
       gd.addCheckbox("Remove branches with end-points", false);
       gd.addNumericField("Dilate Skeleton for viewing by (pixels)", 2, 2);
       gd.showDialog();
       if (gd.wasCanceled()) gdOk = false;
       tubeRadius = (int)gd.getNextNumber();
       vesselRadius = (int)gd.getNextNumber();
       vesselThreshold = (int)gd.getNextNumber();
       minVolume = (int)gd.getNextNumber();
       removeBranches = gd.getNextBoolean();
       dilateSkeleton = (int)gd.getNextNumber();
       return gdOk;
   } 
   
    /**
     * 3D fast filters
     */
    private ImagePlus runFastFilters(ImagePlus img, int filterType, float tubeRad) {

       ImageInt imgInt = ImageInt.wrap(img);
       float radiusXY = tubeRad/(float)cal.pixelWidth;
       float radiusZ = tubeRad/(float)cal.pixelHeight;
       ImageInt imgIntFiltered = FastFilters3D.filterIntImage(imgInt, filterType, radiusXY, radiusXY, radiusZ, ThreadUtil.getNbCpus(), true);
       return (imgInt.getImagePlus());
    }

    // Get objects population
    private Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        labels.setCalibration(img.getCalibration());
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }
    
    public void run(String arg0) {
        
        
        String imageDir = IJ.getDirectory("Choose directory containing TIF files...");
        String outDir = IJ.getDirectory("Choose Directory for output results files...");
        if ((imageDir == null) || (outDir == null)) return;
        

        // get the image files
        File inDir = new File(imageDir);
        String [] imageFile = inDir.list();
        if (imageFile == null) return;
        for (int i = 0; i < imageFile.length; i++) {
            if (imageFile[i].endsWith(".tif")) {
                String imagePath = imageDir + imageFile[i];
                Opener imgOpener = new Opener();
                ImagePlus imgOrg = imgOpener.openImage(imagePath);
                cal = imgOrg.getCalibration();
                if (i == 0) if (createDialog() == false) return; // Ask for parameters for the first image
            
            // Fast filters closing
                ImagePlus imgMax = runFastFilters(imgOrg, FastFilters3D.MAX, tubeRadius);
                ImagePlus imgClose = runFastFilters(imgMax, FastFilters3D.MIN, tubeRadius);
                imgMax.flush();
                
                
            // Run tubeness
                TubenessProcessor tubeP = new TubenessProcessor(vesselRadius,true);
                ImagePlus tubeImg= tubeP.generateImage(imgClose);
                imgClose.flush();
                

            //  Threshold tubeness and binarize
                AutoThresholder at = new AutoThresholder();
                int th = at.getThreshold(AutoThresholder.Method.Yen, tubeImg.getStatistics().histogram16);
                ImageHandler ha = ImageHandler.wrap(tubeImg);
                ImageByte bin = ha.thresholdAboveExclusive(th);
                ImagePlus imgBin = bin.getImagePlus();
                imgBin.setCalibration(cal);
                imgBin.updateAndDraw();
            
            // Find 3D objects    
                IJ.showStatus("Computing object population ...");
                Objects3DPopulation popTmp = getPopFromImage(imgBin);
                
            // filter size keep only object > minVolume and label image
                ImageInt imgLabel = ImageInt.wrap(imgOrg);
                ImageHandler imgObjects = imgLabel.createSameDimensions();
                imgObjects.set332RGBLut();
                imgObjects.setCalibration(cal);
                for (int n = 0; n < popTmp.getNbObjects(); n++) {
                    if (popTmp.getObject(n).getVolumeUnit() > minVolume) {
                        popTmp.getObject(i).draw(imgObjects, 228);
                    }
                }
                imgObjects.getImagePlus().updateAndDraw();
            }
        }      
    }   

}
