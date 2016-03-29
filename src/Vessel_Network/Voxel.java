package Vessel_Network;

public class Voxel implements Comparable<Voxel> { 
	
	
	private int x;
	private int y;
	private int z;
	private int val;
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param val
	 */
	public Voxel(int x, int y, int z, int val) {
		super();
		this.x = x;
		this.y = y;
		this.z = z;
		this.val = val;
	}
	/**
	 * @return the x
	 */
	public int getX() {
		return x;
	}
	/**
	 * @return the y
	 */
	public int getY() {
		return y;
	}
	/**
	 * @return the z
	 */
	public int getZ() {
		return z;
	}
	/**
	 * @return the val
	 */
	public int getVal() {
		return val;
	}
	/**
	 * @param x the x to set
	 */
	public void setX(int x) {
		this.x = x;
	}
	/**
	 * @param y the y to set
	 */
	public void setY(int y) {
		this.y = y;
	}
	/**
	 * @param z the z to set
	 */
	public void setZ(int z) {
		this.z = z;
	}
	/**
	 * @param val the val to set
	 */
	public void setVal(int val) {
		this.val = val;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Voxel [val=" + val + ", x=" + x + ", y=" + y + ", z=" + z + "]";
	}
	@Override
	public int compareTo(Voxel voxel) {
		int z1 = voxel.getVal(); 
	      int z2 = this.getVal(); 
	      if (z1 > z2)  return -1; 
	      else if(z1 == z2) return 0; 
	      else return 1;
	}
	
	

}
