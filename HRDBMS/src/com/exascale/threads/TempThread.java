package com.exascale.threads;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.managers.ResourceManager;

public class TempThread extends HRDBMSThread
{
	private static ConcurrentHashMap<Long, ArrayList<HRDBMSThread>> threads = new ConcurrentHashMap<Long, ArrayList<HRDBMSThread>>();
	private static int FACTOR;
	private static ArrayBlockingQueue<ByteBuffer> cache = new ArrayBlockingQueue<ByteBuffer>(1000000);

	static
	{
		try
		{
			FACTOR = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_concurrent_writers_per_temp_disk"));
			if (HRDBMSWorker.getHParms().getProperty("use_direct_buffers_for_flush").equals("true"))
			{
				int total = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("num_direct"));
				int i = 0;
				while (i < total)
				{
					try
					{
						cache.put(ByteBuffer.allocateDirect(8 * 1024 * 1024));
					}
					catch (Exception e)
					{
						throw e;
					}
					i++;

					if (i % 100 == 0)
					{
						HRDBMSWorker.logger.debug("Allocate direct " + i);
					}
				}
			}
		}
		catch (Throwable f)
		{
			HRDBMSWorker.logger.debug("", f);
			System.exit(1);
			;
		}
	}
	private final HRDBMSThread thread;

	private final long txnum;

	public TempThread(HRDBMSThread thread, long txnum)
	{
		this.thread = thread;
		this.txnum = txnum;
	}

	public static void freeDirect(ByteBuffer bb)
	{
		if (!cache.offer(bb))
		{
			try
			{
				Field cleanerField = bb.getClass().getDeclaredField("cleaner");
				cleanerField.setAccessible(true);
				sun.misc.Cleaner cleaner = (sun.misc.Cleaner)cleanerField.get(bb);
				cleaner.clean();
			}
			catch (Exception e)
			{
			}
		}
	}

	public static ByteBuffer getDirect()
	{
		ByteBuffer retval = cache.poll();
		if (retval == null)
		{
			return retval;
		}
		else
		{
			retval.position(0);
			return retval;
		}
	}

	public static void initialize()
	{
		HRDBMSWorker.logger.debug("TempThread initialize");
	}

	public static void start(HRDBMSThread thread, long txnum)
	{
		new TempThread(thread, txnum).run();
	}

	@Override
	public void run()
	{
		while (true)
		{
			synchronized (threads)
			{
				ArrayList<HRDBMSThread> al = threads.get(txnum);
				if (al == null)
				{
					al = new ArrayList<HRDBMSThread>();
					threads.put(txnum, al);
					thread.start();
					al.add(thread);
					return;
				}

				if (tryHandleNow(al))
				{
					return;
				}

				for (Entry entry : threads.entrySet())
				{
					ArrayList<HRDBMSThread> al2 = (ArrayList<HRDBMSThread>)entry.getValue();

					if (al2 != al)
					{
						int i = al2.size() - 1;
						while (i >= 0)
						{
							HRDBMSThread t = al2.get(i);
							if (t.isDone())
							{
								try
								{
									t.join();
								}
								catch (Exception e)
								{
								}

								al2.remove(i);
							}

							i--;
						}
					}

					if (al2.size() == 0)
					{
						threads.remove(entry.getKey());
					}
				}
			}

			LockSupport.parkNanos(1);
		}
	}

	private boolean tryHandleNow(ArrayList<HRDBMSThread> al)
	{
		if (al.size() < (ResourceManager.TEMP_DIRS.size() * FACTOR))
		{
			thread.start();
			al.add(thread);
			return true;
		}
		else
		{
			int i = al.size() - 1;
			while (i >= 0)
			{
				HRDBMSThread t = al.get(i);
				if (t.isDone())
				{
					try
					{
						t.join();
					}
					catch (Exception e)
					{
					}

					al.remove(i);
				}

				i--;
			}

			if (al.size() < (ResourceManager.TEMP_DIRS.size() * FACTOR))
			{
				thread.start();
				al.add(thread);
				return true;
			}
		}

		return false;
	}
}
