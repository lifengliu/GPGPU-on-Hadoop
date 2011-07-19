package clustering;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import lightLogger.Logger;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLBuildException;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLDevice;
import com.nativelibs4java.opencl.CLDevice.QueueProperties;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLException;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLPlatform;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;

/**
 * First OpenCL implementation of IKMeansBasic. No optimization and unfinished
 * kernel. NOTE: estimation - result is too slow!
 * 
 * @author christof
 * 
 */
public class KMeansBasicCL implements IKMeansBasic {

	private static final Class<KMeansBasicCL> CLAZZ = KMeansBasicCL.class;

	private static final String KERNEL_PATH = "../KMeansBasicCL.cl";
	private static final String SQUARE_SUM = "squareSum";
	private static final String SUM = "sum";
	private static final int SIZEOF_CL_FLOAT = 4;

	private CLPlatform[] platforms;
	private ArrayList<CLDevice> devices;
	private CLContext context;
	private CLQueue cmdQ;
	private CLProgram program;
	private CLKernel kernel;

	private HashMap<String, CLKernel> kernels = new HashMap<String, CLKernel>();

	private int dim;

	public static enum TYPES {
		CL_CPU, CL_GPU
	};

	public static final float DISTANCE_ERROR = -1;
	public static final IPoint CENTROID_ERROR = null;

	public KMeansBasicCL(int dim) {
		this.dim = dim;
	}

	@Override
	public float computeDistance(IPoint p, IPoint c) {
		try {
			kernel = this.getKernel(SQUARE_SUM);

			// Prepate Data
			CLBuffer<FloatBuffer> pBuffer = context.createFloatBuffer(
					CLMem.Usage.Input, FloatBuffer.wrap(p.getDims()), true);
			CLBuffer<FloatBuffer> cBuffer = context.createFloatBuffer(
					CLMem.Usage.Input, FloatBuffer.wrap(c.getDims()), true);
			CLBuffer<FloatBuffer> resBuffer = context.createFloatBuffer(
					CLMem.Usage.Output, 1);

			cmdQ.finish();

			int globalSize = p.getDims().length;
			int localSize = p.getDims().length;

			kernel.setArg(0, resBuffer);
			kernel.setArg(1, pBuffer);
			kernel.setArg(2, cBuffer);
			kernel.setLocalArg(3, localSize * SIZEOF_CL_FLOAT);

			// Run kernel
			// CLEvent event =
			kernel.enqueueNDRange(cmdQ, new int[] { globalSize },
					new int[] { localSize }, new CLEvent[0]);

			cmdQ.finish();

			// Get results - first value in array
			FloatBuffer res = ByteBuffer.allocateDirect(1 * SIZEOF_CL_FLOAT)
					.order(context.getByteOrder()).asFloatBuffer();
			resBuffer.read(cmdQ, 0, 1, res, true, new CLEvent[0]);
			res.rewind();
			return (float) Math.sqrt(res.get(0));
		} catch (CLException err) {
			Logger.logError(CLAZZ, "OpenCL error:\n" + err.getMessage() + "():"
					+ err.getCode());
			err.printStackTrace();
			return DISTANCE_ERROR;
		} catch (Exception err) {
			Logger.logError(CLAZZ, "Error:\n" + err.getMessage() + "()");
			err.printStackTrace();
			return DISTANCE_ERROR;
		}
	}

	@Override
	public IPoint computeCentroid(List<ICPoint> points) {
		IPoint centroid = new Point(this.dim);
		try {
			kernel = this.getKernel(SUM);
			float p1Dim[] = new float[points.size()];

			// Prepate Data
			CLBuffer<FloatBuffer> resBuffer = context.createFloatBuffer(
					CLMem.Usage.Output, 1);
			FloatBuffer res = ByteBuffer.allocateDirect(1 * SIZEOF_CL_FLOAT)
					.order(context.getByteOrder()).asFloatBuffer();

			CLBuffer<FloatBuffer> pBuffer;

			for (int d = 0; d < this.dim; d++) {
				for (int i = 0; i < points.size(); i++)
					p1Dim[i] = points.get(i).get(d);

				pBuffer = context.createFloatBuffer(CLMem.Usage.Input,
						FloatBuffer.wrap(p1Dim), true);

				cmdQ.finish();

				int globalSize = p1Dim.length;
				int localSize = p1Dim.length;

				kernel.setArg(0, resBuffer);
				kernel.setArg(1, pBuffer);
				kernel.setLocalArg(2, localSize * SIZEOF_CL_FLOAT);

				// Run kernel
				// CLEvent event =
				kernel.enqueueNDRange(cmdQ, new int[] { globalSize },
						new int[] { localSize }, new CLEvent[0]);

				cmdQ.finish();

				// Get results - first value in array
				resBuffer.read(cmdQ, 0, 1, res, true, new CLEvent[0]);
				res.rewind();
				centroid.set(d, res.get(0) / points.size());
			}
			return centroid;
		} catch (CLException err) {
			Logger.logError(CLAZZ, "OpenCL error:\n" + err.getMessage() + "():"
					+ err.getCode());
			err.printStackTrace();
			return CENTROID_ERROR;
		} catch (Exception err) {
			Logger.logError(CLAZZ, "Error:\n" + err.getMessage() + "()");
			err.printStackTrace();
			return CENTROID_ERROR;
		}
	}

	private CLKernel getKernel(String name) throws CLBuildException {
		CLKernel kernel = this.kernels.get(name);
		if (kernel == null) {
			kernel = program.createKernel(name);
			kernels.put(name, kernel);
		}
		return kernel;
	}

	@Override
	public int getDim() {
		return this.dim;
	}

	public boolean initialize(TYPES type) {
		if (type == TYPES.CL_CPU)
			return this.initialize(CLDevice.Type.CPU);
		else if (type == TYPES.CL_GPU)
			return this.initialize(CLDevice.Type.GPU);
		else
			return false;
	}

	public boolean initialize(CLDevice.Type type) {
		try {
			// Init OpenCL
			platforms = JavaCL.listPlatforms();

			EnumSet<CLDevice.Type> types = EnumSet.of(type);
			devices = new ArrayList<CLDevice>();
			CLDevice[] devTmp;

			for (CLPlatform platform : platforms) {
				devTmp = platform.listDevices(types, true);
				devices.addAll(Arrays.asList(devTmp));
			}

			devTmp = new CLDevice[1];
			devTmp[0] = devices.get(0);
			context = JavaCL.createContext(null, devTmp);
			cmdQ = context.createDefaultQueue(QueueProperties.ProfilingEnable);

			String src = readFile(KERNEL_PATH);

			program = context.createProgram(src);

			try {
				program.build();
			} catch (Exception err) {
				Logger.logError(CLAZZ, "Build log for \"" + devices.get(0)
						+ "\n" + err.getMessage());
				return false;
			}

			return true;
		} catch (CLException err) {
			Logger.logError(CLAZZ, "OpenCL error:\n" + err.getMessage() + "():"
					+ err.getCode());
			err.printStackTrace();
			return false;
		} catch (Exception err) {
			Logger.logError(CLAZZ, "Error:\n" + err.getMessage() + "()");
			err.printStackTrace();
			return false;
		}
	}

	protected static int calcWorkGroupSize(int globalSize,
			final long MAX_GROUP_SIZE) {
		int localSize = (int) MAX_GROUP_SIZE;
		if (globalSize < localSize)
			localSize = globalSize;
		else
			while (globalSize % localSize != 0)
				--localSize;
		return localSize;
	}

	protected static String readFile(String fName) {
		StringBuffer sb = new StringBuffer();
		try {
			Scanner sc = new Scanner(CLAZZ.getResourceAsStream(fName));
			while (sc.hasNext())
				sb.append(sc.nextLine());
			sc.close();
		} catch (Exception e) {
			Logger.logError(CLAZZ, "Could not read file: " + fName);
			e.printStackTrace();
		}
		return sb.toString();
	}

}
