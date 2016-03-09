/*
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 */
package org.biojava.nbio.structure.symmetry.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Matrix4d;

import org.biojava.nbio.structure.symmetry.utils.SymmetryTools;

/**
 * Data Structure that stores all the symmetry axis that describe
 * the symmetry of a structure. Generalizes to all types of symmetry,
 * the classic ones (Cn, Dn) and any hierarchical or local symmetries.
 * <p>
 * It also stores the parts of the structure (symmetric units) involved
 * in each axis, in addition to the way to calculate them.
 * <p>
 * This is intended to provide a general axis support for the multiple
 * repeat alignment optimization and the axis display in Jmol. This
 * object is related to a MultipleAlignment object that defines the
 * symmetric units.
 *
 * @author Aleix Lafita
 * @since 4.2.0
 *
 */
public class SymmetryAxes {

	/**
	 * List of all symmetry axis. They are sorted from higher to lower
	 * in the symmetry hierarchy, where higher means that they apply
	 * more globally and lower means that they apply to a local region
	 * of the higher axis division.
	 */
	private List<Matrix4d> axes;

	/**
	 * Tree of the symmetry axes hierarchy of a structure. Every index
	 * is the axis index and the integer stored means the number of parts
	 * this axis divides the structure in.
	 */
	private List<Integer> tree;

	/**
	 * Matrix of size [repeats][axes]. The first index of the matrix
	 * indicates which repeat is considered, and the second index
	 * indicates which axis. The integer stored means how many times
	 * the transformation has to be applied to the repeat, and a value
	 * of 0 means that this repeat is not affected by the axis.
	 */
	private List<List<Integer>> repeatTransforms;

	/**
	 * The list of integers defines which repeats are involved in this axis
	 * and how the axis transformation is computed. The list has size 2 for
	 * the first index and any size for the second index. Describes that all
	 * repeats in the first list superimposed to all repeats of the second
	 * list should generate the axis transformation.
	 */
	private Map<Integer, List<List<Integer>>> mapAxisRepeats;

	/**
	 * Constructor.
	 * Initializes variables only.
	 */
	public SymmetryAxes(){
		axes = new ArrayList<Matrix4d>();
		mapAxisRepeats = new HashMap<Integer, List<List<Integer>>>();
		repeatTransforms = new ArrayList<List<Integer>>();
		tree = new ArrayList<Integer>();
	}

	/**
	 * Adds a new axis of symmetry.
	 * The repeats that participate in this axis and their superposition
	 * relation should also be indicated.
	 *
	 * @param axis the new axis of symmetry found
	 * @param superposition repeats participating and superposition relation
	 * @param repeats number of times the transformation is applied to every
	 * 			repeat. index1=repeat, index2=times.
	 * @param division number of parts that this axis divides the structure in
	 *
	 * @throws IllegalArgumentException if the repeat relation is in the
	 * 			wrong format: should be double List of equal sizes.
	 */
	public void addAxis(Matrix4d axis, List<List<Integer>> superposition,
			List<Integer> repeats, Integer division) {

		//Check correct format of repeat relations
		if (superposition.size() != 2){
			throw new IllegalArgumentException(
					"Wrong superposition format: should be double List.");
		} else if (superposition.get(0).size() != superposition.get(1).size()){
			throw new IllegalArgumentException(
					"Wrong superposition format: not equal List sizes.");
		}
		if (division < 2) throw new IllegalArgumentException(
				"A symmetry axis should divide a structure in > 2 parts");

		axes.add(axis);
		tree.add(division);

		//Extend the double List by the necessary rows
		while (repeatTransforms.size() < repeats.size()){
			List<Integer> list = new ArrayList<Integer>();
			for (int a=0; a<axes.size()-1; a++){
				list.add(0);
			}
			repeatTransforms.add(list);
		}
		for (int su=0; su<repeatTransforms.size(); su++){
			Integer nTimes = 0;
			if (su<repeats.size()){
				nTimes = repeats.get(su);
			}
			repeatTransforms.get(su).add(nTimes);
		}

		//Check that the repeat indices match
		for (int c=0; c<2; c++){
			for (int p=0; p<superposition.get(c).size(); p++){
				Integer su = superposition.get(c).get(p);
				if (su>repeatTransforms.size()){
					throw new IllegalArgumentException(
							"Repeat indicex in superposition out of bounds");
				}
			}
		}
		mapAxisRepeats.put(axes.size()-1, superposition);
	}

	/**
	 * Updates an axis of symmetry, after the superposition changed.
	 *
	 * @param index old axis index
	 * @param newAxis
	 */
	public void updateAxis(Integer index, Matrix4d newAxis){
		axes.set(index, newAxis);
	}

	/**
	 * Return all elementary axes of symmetry of the structure, that is,
	 * the axes stored in the List as unique and from which all the symmetry
	 * axes are constructed.
	 *
	 * @return axes elementary axes of symmetry.
	 */
	public List<Matrix4d> getElementaryAxes(){
		return axes;
	}

	/**
	 * Return the repeat superposition relation needed to obtain the
	 * axis of symmetry (which repeats and in which order have to be
	 * superimposed to obtain the axis).
	 *
	 * @param index the axis index
	 * @return the double List of repeat relations, or null if the
	 * 			axis is not already stored.
	 */
	public List<List<Integer>> getRepeatRelation(Integer index){
		return mapAxisRepeats.get(index);
	}


	/**
	 * Return the transformation that needs to be applied to a determinate
	 * repeat so that all get superimposed to the same point.
	 *
	 * @param repeat the repeat index
	 * @return transformation matrix for the repeat
	 */
	public Matrix4d getRepeatTransform(int repeat){

		List<Matrix4d> allTransforms = new ArrayList<Matrix4d>();
		Matrix4d transform = new Matrix4d();
		transform.setIdentity();

		for (int a=0; a<axes.size(); a++){
			Matrix4d t = (Matrix4d) axes.get(a).clone();
			Matrix4d clone = (Matrix4d) t.clone();
			//Pack the Matrices when they are equal
			for (int i=1; i<repeatTransforms.get(repeat).get(a); i++){
				clone.mul(t);
			}
			if (repeatTransforms.get(repeat).get(a)>0) allTransforms.add(clone);
		}
		//Multiply the matrices in the inverse order as they have to be applied
		//for (int t=0; t<allTransforms.size(); t++){
		for (int t=allTransforms.size()-1; t>=0; t--){
			transform.mul(allTransforms.get(t));
		}

		return transform;
	}

	/**
	 * Return all symmetry axes of of the structure: the set of axes that
	 * describe all parts of the structure. This combines the elementary
	 * axes to generate all possible axes.
	 * Use this method to display the axes.
	 *
	 * @return axes all symmetry axes of the structure.
	 */
	public List<Matrix4d> getSymmetryAxes(){

		List<Matrix4d> symmAxes = new ArrayList<Matrix4d>();

		//For every repeat do
		for (int su=0; su<repeatTransforms.size(); su++){
			Matrix4d axis = new Matrix4d();
			axis.setIdentity();
			boolean ident = true;

			//Consider all the axes, in the inverse order
			for (int t=repeatTransforms.get(su).size()-1; t>=0; t--){
				int repeats = repeatTransforms.get(su).get(t);
				Matrix4d clone = (Matrix4d) axes.get(t).clone();
				Matrix4d invert = (Matrix4d) clone.clone();
				invert.invert();

				//Do for every repeat
				for (int n=0; n<repeats; n++){
					if (ident) {
						axis = clone;
						clone = (Matrix4d) axes.get(t).clone();
						ident = false;
					} else {
						clone.mul(axis);
						axis = clone;
						clone = (Matrix4d) axes.get(t).clone();
						axis.mul(invert);
					}
				}
			}
			if (!ident) symmAxes.add(axis);
		}

		//Ensure the axis are not equivalent
		for (int a=0; a<symmAxes.size(); a++){
			for (int b=a+1; b<symmAxes.size(); b++){
				Matrix4d ax = symmAxes.get(a);
				Matrix4d bx = symmAxes.get(b);
				boolean eq = SymmetryTools.equivalentAxes(ax, bx, 0.1);
				if (eq){
					symmAxes.set(a, bx);
				}
			}
		}
		return symmAxes;
	}
}
