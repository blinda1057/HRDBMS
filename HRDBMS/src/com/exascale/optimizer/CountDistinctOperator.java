package com.exascale.optimizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.managers.ResourceManager;
import com.exascale.misc.BufferedFileChannel;
import com.exascale.misc.MurmurHash;
import com.exascale.misc.MyDate;
import com.exascale.threads.HRDBMSThread;

public final class CountDistinctOperator implements AggregateOperator, Serializable
{
	private static sun.misc.Unsafe unsafe;

	static
	{
		try
		{
			final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (sun.misc.Unsafe)f.get(null);
		}
		catch (Exception e)
		{
			unsafe = null;
		}
	}

	private String input;
	private String output;
	private transient final MetaData meta;

	private long NUM_GROUPS = 16;

	private long childCard = 16 * 16;

	public CountDistinctOperator(String input, String output, MetaData meta)
	{
		this.input = input;
		this.output = output;
		this.meta = meta;
	}

	public static CountDistinctOperator deserialize(InputStream in, HashMap<Long, Object> prev) throws Exception
	{
		CountDistinctOperator value = (CountDistinctOperator)unsafe.allocateInstance(CountDistinctOperator.class);
		prev.put(OperatorUtils.readLong(in), value);
		value.input = OperatorUtils.readString(in, prev);
		value.output = OperatorUtils.readString(in, prev);
		value.NUM_GROUPS = OperatorUtils.readLong(in);
		value.childCard = OperatorUtils.readLong(in);
		return value;
	}

	@Override
	public CountDistinctOperator clone()
	{
		return new CountDistinctOperator(input, output, meta);
	}

	@Override
	public AggregateResultThread getHashThread(HashMap<String, Integer> cols2Pos)
	{
		return new CountDistinctHashThread(cols2Pos);
	}

	@Override
	public String getInputColumn()
	{
		return input;
	}

	@Override
	public AggregateResultThread newProcessingThread(ArrayList<ArrayList<Object>> rows, HashMap<String, Integer> cols2Pos)
	{
		return new CountDistinctThread(rows, cols2Pos);
	}

	@Override
	public String outputColumn()
	{
		return output;
	}

	@Override
	public String outputType()
	{
		return "LONG";
	}

	@Override
	public void serialize(OutputStream out, IdentityHashMap<Object, Long> prev) throws Exception
	{
		Long id = prev.get(this);
		if (id != null)
		{
			OperatorUtils.serializeReference(id, out);
			return;
		}

		OperatorUtils.writeType(52, out);
		prev.put(this, OperatorUtils.writeID(out));
		OperatorUtils.writeString(input, out, prev);
		OperatorUtils.writeString(output, out, prev);
		OperatorUtils.writeLong(NUM_GROUPS, out);
		OperatorUtils.writeLong(childCard, out);
	}

	public void setChildCard(long card)
	{
		childCard = card;
	}

	@Override
	public void setInput(String col)
	{
		input = col;
	}

	@Override
	public void setInputColumn(String col)
	{
		input = col;
	}

	@Override
	public void setNumGroups(long groups)
	{
		NUM_GROUPS = groups;
	}

	private final class CountDistinctHashThread extends AggregateResultThread
	{
		// private final DiskBackedALOHashMap<AtomicLong> results = new
		// DiskBackedALOHashMap<AtomicLong>(NUM_GROUPS > 0 ? NUM_GROUPS : 16);
		private ConcurrentHashMap<ArrayList<Object>, AtomicLong> results = new ConcurrentHashMap<ArrayList<Object>, AtomicLong>(NUM_GROUPS <= Integer.MAX_VALUE ? (int)NUM_GROUPS : Integer.MAX_VALUE, 0.75f, 6 * ResourceManager.cpus);
		private ConcurrentHashMap<ByteBuffer, ByteBuffer> hashSet;
		private final int pos;
		private boolean inMem = true;
		private ArrayList<String> externalFiles;
		private ArrayList<ArrayList<byte[]>> bins;
		final int numBins = 257;
		private int size;
		private HashMap<Integer, FlushBinThread> threads;
		private ArrayList<FileChannel> channels;
		private byte[] types;
		private volatile boolean done = false;
		private ArrayList<RandomAccessFile> files1;

		public CountDistinctHashThread(HashMap<String, Integer> cols2Pos)
		{
			pos = cols2Pos.get(input);
			if (ResourceManager.criticalMem())
			{
				inMem = false;
			}
			else if (childCard > ResourceManager.QUEUE_SIZE * Double.parseDouble(HRDBMSWorker.getHParms().getProperty("hash_external_factor")))
			{
				inMem = false;
			}

			if (inMem)
			{
				hashSet = new ConcurrentHashMap<ByteBuffer, ByteBuffer>(childCard <= Integer.MAX_VALUE ? (int)childCard : Integer.MAX_VALUE);
			}
			else
			{
				try
				{
					externalFiles = new ArrayList<String>(numBins);
					ArrayList<String> fns1 = createFNs(numBins, 0);
					externalFiles.addAll(fns1);
					files1 = createFiles(fns1);
					channels = createChannels(files1);
					bins = new ArrayList<ArrayList<byte[]>>();
					threads = new HashMap<Integer, FlushBinThread>();
					size = (int)(ResourceManager.QUEUE_SIZE * Double.parseDouble(HRDBMSWorker.getHParms().getProperty("external_factor")) / (numBins >> 3));
					int i = 0;
					while (i < numBins)
					{
						bins.add(new ArrayList<byte[]>(size));
						i++;
					}
				}
				catch (Exception e)
				{
					HRDBMSWorker.logger.debug("", e);
				}
			}
		}

		@Override
		public void close()
		{
			hashSet = null;
			results = null;
		}

		@Override
		public Object getResult(ArrayList<Object> keys)
		{
			if (inMem)
			{
				return results.get(keys).longValue();
			}

			if (!done)
			{
				synchronized (this)
				{
					if (!done)
					{
						int i = 0;
						int z = 0;
						final int limit = bins.size();
						// for (ArrayList<byte[]> bin : bins)
						while (z < limit)
						{
							ArrayList<byte[]> bin = bins.get(z++);
							if (bin.size() > 0)
							{
								FlushBinThread thread = new FlushBinThread(bin, types, channels.get(i));
								if (threads.putIfAbsent(i, thread) != null)
								{
									while (true)
									{
										try
										{
											threads.get(i).join();
											break;
										}
										catch (InterruptedException e)
										{
										}
									}
									if (!threads.get(i).getOK())
									{
										return threads.get(i).getException();
									}

									threads.put(i, thread);
								}
								thread.start();
							}

							i++;
						}

						for (FlushBinThread thread : threads.values())
						{
							while (true)
							{
								try
								{
									thread.join();
									break;
								}
								catch (InterruptedException e)
								{
								}
							}
							if (!thread.getOK())
							{
								return thread.getException();
							}
						}

						HashDataThread thread5 = new HashDataThread(channels.get(0), types, results);
						thread5.start();
						HashDataThread thread6 = new HashDataThread(channels.get(1), types, results);
						thread6.start();
						ArrayList<HashDataThread> rightThreads = new ArrayList<HashDataThread>();
						rightThreads.add(thread5);
						rightThreads.add(thread6);

						i = 2;

						while (true)
						{
							if (rightThreads.size() == 0)
							{
								break;
							}

							HashDataThread right = rightThreads.remove(0);
							while (true)
							{
								try
								{
									right.join();
									break;
								}
								catch (InterruptedException e)
								{
								}
							}
							if (!right.getOK())
							{
								return right.getException();
							}

							if (i < numBins)
							{
								HashDataThread right2 = new HashDataThread(channels.get(i++), types, results);
								right2.start();
								rightThreads.add(right2);
							}
						}

						for (FileChannel fc : channels)
						{
							try
							{
								fc.close();
							}
							catch (Exception e)
							{
							}
						}

						for (RandomAccessFile raf : files1)
						{
							try
							{
								raf.close();
							}
							catch (Exception e)
							{
							}
						}

						for (String fn : externalFiles)
						{
							try
							{
								new File(fn).delete();
							}
							catch (Exception e)
							{
							}
						}

						done = true;
					}
				}
			}

			return results.get(keys).longValue();
		}

		// @Parallel
		@Override
		public final void put(ArrayList<Object> row, ArrayList<Object> group) throws Exception
		{
			final ArrayList<Object> consolidated = new ArrayList<Object>();
			consolidated.addAll(group);
			consolidated.add(row.get(pos));
			if (inMem)
			{
				if (types == null)
				{
					byte[] types1 = new byte[consolidated.size()];
					int j = 0;
					for (Object o : consolidated)
					{
						if (o instanceof Integer)
						{
							types1[j] = (byte)1;
						}
						else if (o instanceof Double)
						{
							types1[j] = (byte)2;
						}
						else if (o instanceof String)
						{
							types1[j] = (byte)4;
						}
						else if (o instanceof Long)
						{
							types1[j] = (byte)0;
						}
						else if (o instanceof MyDate)
						{
							types1[j] = (byte)3;
						}
						else
						{
							throw new Exception("Unknown type: " + o.getClass());
						}

						j++;
					}

					types = types1;
				}
				byte[] c = toBytes(consolidated, types);
				ByteBuffer bb = ByteBuffer.wrap(c);
				if (hashSet.putIfAbsent(bb, bb) == null)
				{
					final AtomicLong al = results.get(group);
					if (al != null)
					{
						al.incrementAndGet();
						return;
					}

					if (results.putIfAbsent(group, new AtomicLong(1)) != null)
					{
						results.get(group).incrementAndGet();
					}
				}
			}
			else
			{
				if (types == null)
				{
					byte[] types1 = new byte[consolidated.size()];
					int j = 0;
					for (Object o : consolidated)
					{
						if (o instanceof Integer)
						{
							types1[j] = (byte)1;
						}
						else if (o instanceof Double)
						{
							types1[j] = (byte)2;
						}
						else if (o instanceof String)
						{
							types1[j] = (byte)4;
						}
						else if (o instanceof Long)
						{
							types1[j] = (byte)0;
						}
						else if (o instanceof MyDate)
						{
							types1[j] = (byte)3;
						}
						else
						{
							throw new Exception("Unknown type: " + o.getClass());
						}

						j++;
					}

					types = types1;
				}
				byte[] data = toBytes(consolidated, types);
				final long hash = 0x7FFFFFFFFFFFFFFFL & hash(data);
				int x = (int)(hash % numBins);

				synchronized (bins)
				{
					ArrayList<byte[]> bin = bins.get(x);
					// writeToHashTable(hash, (ArrayList<Object>)o);
					bin.add(data);

					if (bin.size() == size)
					{
						FlushBinThread thread = new FlushBinThread(bin, types, channels.get(x));
						if (threads.putIfAbsent(x, thread) != null)
						{
							threads.get(x).join();
							if (!threads.get(x).getOK())
							{
								throw threads.get(x).getException();
							}

							threads.put(x, thread);
						}
						thread.start();
						bins.set(x, new ArrayList<byte[]>(size));
					}
				}
			}
		}

		private ArrayList<FileChannel> createChannels(ArrayList<RandomAccessFile> files)
		{
			ArrayList<FileChannel> retval = new ArrayList<FileChannel>(files.size());
			for (RandomAccessFile raf : files)
			{
				retval.add(raf.getChannel());
			}

			return retval;
		}

		private ArrayList<RandomAccessFile> createFiles(ArrayList<String> fns) throws Exception
		{
			ArrayList<RandomAccessFile> retval = new ArrayList<RandomAccessFile>(fns.size());
			for (String fn : fns)
			{
				while (true)
				{
					try
					{
						RandomAccessFile raf = new RandomAccessFile(fn, "rw");
						retval.add(raf);
						break;
					}
					catch (FileNotFoundException e)
					{
						ResourceManager.panic = true;
						try
						{
							Thread.sleep(Integer.parseInt(HRDBMSWorker.getHParms().getProperty("rm_sleep_time_ms")) / 2);
						}
						catch (Exception f)
						{
						}
					}
				}
			}

			return retval;
		}

		private ArrayList<String> createFNs(int num, int extra)
		{
			ArrayList<String> retval = new ArrayList<String>(num);
			int i = 0;
			while (i < num)
			{
				String fn = ResourceManager.TEMP_DIRS.get(i % ResourceManager.TEMP_DIRS.size()) + this.hashCode() + "" + System.currentTimeMillis() + i + "_" + extra + ".exthash";
				retval.add(fn);
				i++;
			}

			return retval;
		}

		private long hash(byte[] data) throws Exception
		{
			return MurmurHash.hash64(data, data.length);
		}

		private final byte[] toBytes(Object v, byte[] types) throws Exception
		{
			ArrayList<byte[]> bytes = new ArrayList<byte[]>();
			ArrayList<Object> val = (ArrayList<Object>)v;

			int size = 4;
			int i = 0;
			for (byte b : types)
			{
				if (b == 0 || b == 2)
				{
					size += 8;
				}
				else if (b == 1 || b == 3)
				{
					size += 4;
				}
				else if (b == 4)
				{
					String s = (String)val.get(i);
					byte[] bs = s.getBytes(StandardCharsets.UTF_8);
					size += (4 + bs.length);
					bytes.add(bs);
				}
				else
				{
					throw new Exception("Unknown type: " + types[i]);
				}

				i++;
			}
			final byte[] retval = new byte[size];
			// System.out.println("In toBytes(), row has " + val.size() +
			// " columns, object occupies " + size + " bytes");
			final ByteBuffer retvalBB = ByteBuffer.wrap(retval);
			retvalBB.putInt(size - 4);
			int x = 0;
			i = 0;
			int z = 0;
			final int limit = val.size();
			// for (final Object o : val)
			while (z < limit)
			{
				Object o = val.get(z++);
				if (types[i] == 0)
				{
					retvalBB.putLong((Long)o);
				}
				else if (types[i] == 1)
				{
					retvalBB.putInt((Integer)o);
				}
				else if (types[i] == 2)
				{
					retvalBB.putDouble((Double)o);
				}
				else if (types[i] == 3)
				{
					retvalBB.putInt(((MyDate)o).getTime());
				}
				else if (types[i] == 4)
				{
					byte[] temp = bytes.get(x);
					x++;
					retvalBB.putInt(temp.length);
					retvalBB.put(temp);
				}

				i++;
			}

			return retval;
		}
	}

	private final class CountDistinctThread extends AggregateResultThread
	{
		private final ArrayList<ArrayList<Object>> rows;
		private long result;
		private final int pos;
		private final HashSet<Object> distinct = new HashSet<Object>();

		public CountDistinctThread(ArrayList<ArrayList<Object>> rows, HashMap<String, Integer> cols2Pos)
		{
			this.rows = rows;
			pos = cols2Pos.get(input);
		}

		@Override
		public void close()
		{
		}

		@Override
		public Object getResult()
		{
			return new Long(result);
		}

		@Override
		public void run()
		{
			int z = 0;
			final int limit = rows.size();
			// for (final Object o : rows)
			while (z < limit)
			{
				final Object o = rows.get(z++);
				final ArrayList<Object> row = (ArrayList<Object>)o;
				final Object val = row.get(pos);
				distinct.add(val);
			}

			result = distinct.size();
		}
	}

	private class FlushBinThread extends HRDBMSThread
	{
		private final ArrayList<byte[]> bin;
		private final FileChannel fc;
		private boolean ok = true;
		private Exception e;

		public FlushBinThread(ArrayList<byte[]> bin, byte[] types, FileChannel fc)
		{
			this.bin = bin;
			this.fc = fc;
		}

		public Exception getException()
		{
			return e;
		}

		public boolean getOK()
		{
			return ok;
		}

		@Override
		public void run()
		{
			// Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			try
			{
				int length = 0;
				int z = 0;
				final int limit = bin.size();
				// for (byte[] b : bin)
				while (z < limit)
				{
					byte[] b = bin.get(z++);
					length += b.length;
				}

				byte[] data = new byte[length];
				length = 0;
				z = 0;
				// for (byte[] b : bin)
				while (z < limit)
				{
					byte[] b = bin.get(z++);
					System.arraycopy(b, 0, data, length, b.length);
					length += b.length;
				}
				bin.clear();
				fc.position(fc.size());
				ByteBuffer bb = ByteBuffer.wrap(data);
				fc.write(bb);
			}
			catch (Exception e)
			{
				ok = false;
				this.e = e;
			}
		}
	}

	private class HashDataThread extends HRDBMSThread
	{
		private FileChannel fc;
		private boolean ok = true;
		private Exception e;
		private final byte[] types;
		private final ConcurrentHashMap<ArrayList<Object>, AtomicLong> results;
		private HashMap<ArrayList<Object>, ArrayList<Object>> set = new HashMap<ArrayList<Object>, ArrayList<Object>>();

		public HashDataThread(FileChannel fc, byte[] types, ConcurrentHashMap<ArrayList<Object>, AtomicLong> results)
		{
			try
			{
				this.fc = new BufferedFileChannel(fc, 8 * 1024 * 1024);
			}
			catch (Exception e)
			{
				this.fc = fc;
			}
			this.types = types;
			this.results = results;
		}

		public Exception getException()
		{
			return e;
		}

		public boolean getOK()
		{
			return ok;
		}

		@Override
		public void run()
		{
			try
			{
				fc.position(0);
				ByteBuffer bb1 = ByteBuffer.allocate(4);
				while (true)
				{
					bb1.position(0);
					if (fc.read(bb1) == -1)
					{
						set = null;
						return;
					}
					bb1.position(0);
					int length = bb1.getInt();
					ByteBuffer bb = ByteBuffer.allocate(length);
					fc.read(bb);
					ArrayList<Object> row = (ArrayList<Object>)fromBytes(bb.array(), types);
					ArrayList<Object> group = new ArrayList<Object>(row.size() - 1);
					int i = 0;
					while (i < row.size() - 1)
					{
						group.add(row.get(i++));
					}

					if (set.get(row) == null)
					{
						set.put(row, row);
						AtomicLong al = results.get(group);
						if (al != null)
						{
							al.incrementAndGet();
						}
						else
						{
							results.put(group, new AtomicLong(1));
						}
					}
				}
			}
			catch (Exception e)
			{
				ok = false;
				this.e = e;
			}
		}

		private final Object fromBytes(byte[] val, byte[] types) throws Exception
		{
			final ByteBuffer bb = ByteBuffer.wrap(val);
			final int numFields = types.length;

			if (numFields == 0)
			{
				return new ArrayList<Object>();
			}

			final ArrayList<Object> retval = new ArrayList<Object>(numFields);
			int i = 0;
			while (i < numFields)
			{
				if (types[i] == 0)
				{
					// long
					final Long o = bb.getLong();
					retval.add(o);
				}
				else if (types[i] == 1)
				{
					// integer
					final Integer o = bb.getInt();
					retval.add(o);
				}
				else if (types[i] == 2)
				{
					// double
					final Double o = bb.getDouble();
					retval.add(o);
				}
				else if (types[i] == 3)
				{
					// date
					final MyDate o = new MyDate(bb.getInt());
					retval.add(o);
				}
				else if (types[i] == 4)
				{
					// string
					final int length = bb.getInt();
					final byte[] temp = new byte[length];
					bb.get(temp);
					try
					{
						final String o = new String(temp, StandardCharsets.UTF_8);
						retval.add(o);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
				else
				{
					HRDBMSWorker.logger.error("Unknown type in fromBytes(): " + types[i]);
					HRDBMSWorker.logger.debug("So far the row is " + retval);
					throw new Exception("Unknown type in fromBytes(): " + types[i]);
				}

				i++;
			}

			return retval;
		}
	}
}
