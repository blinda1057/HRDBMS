package com.exascale.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.tables.Transaction;

public final class Phase4
{
	private static final int MAX_LOCAL_NO_HASH_PRODUCT = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_local_no_hash_product")); // 1000000
	private static final int MAX_LOCAL_LEFT_HASH = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_local_left_hash")); // 1000000
	private static final int MAX_LOCAL_SORT = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_local_sort")); // 1000000
	private static final int MAX_CARD_BEFORE_HASH = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_card_before_hash")); // 500000
	public static AtomicInteger id = new AtomicInteger(0);
	private final RootOperator root;
	private final MetaData meta;
	private final int MAX_INCOMING_CONNECTIONS = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_neighbor_nodes")); // 100
	private final Transaction tx;

	private final HashMap<Operator, Long> cCache = new HashMap<Operator, Long>();

	private boolean lt = true;

	public Phase4(RootOperator root, Transaction tx)
	{
		this.root = root;
		this.tx = tx;
		meta = root.getMeta();
	}

	public static void clearOpParents(Operator op, HashSet<Operator> touched)
	{
		if (touched.contains(op))
		{
			return;
		}

		if (op instanceof TableScanOperator)
		{
			((TableScanOperator)op).clearOpParents();
			touched.add(op);
		}
		else
		{
			touched.add(op);
			for (final Operator o : op.children())
			{
				clearOpParents(o, touched);
			}
		}
	}

	public long card(Operator op) throws Exception
	{
		final Long r = cCache.get(op);
		if (r != null)
		{
			return r;
		}

		if (op instanceof AntiJoinOperator)
		{
			final long retval = (long)((1 - meta.likelihood(((AntiJoinOperator)op).getHSHM(), root, tx, op)) * card(op.children().get(0)));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof CaseOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof ConcatOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof DateMathOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof ExceptOperator)
		{
			long card = card(op.children().get(0));
			cCache.put(op, card);
			return card;
		}

		if (op instanceof ExtendOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof ExtendObjectOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof HashJoinOperator)
		{
			HashSet<HashMap<Filter, Filter>> hshm = ((HashJoinOperator)op).getHSHM();
			double max = -1;
			for (HashMap<Filter, Filter> hm : hshm)
			{
				double temp = meta.likelihood(new ArrayList<Filter>(hm.keySet()), tx, op);
				if (temp > max)
				{
					max = temp;
				}
			}
			final long retval = (long)(card(op.children().get(0)) * card(op.children().get(1)) * max);
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof IntersectOperator)
		{
			long lCard = card(op.children().get(0));
			long rCard = card(op.children().get(1));

			if (lCard <= rCard)
			{
				cCache.put(op, lCard);
				return lCard;
			}
			else
			{
				cCache.put(op, rCard);
				return rCard;
			}
		}

		if (op instanceof MultiOperator)
		{
			// return card(op.children().get(0));
			final long groupCard = meta.getColgroupCard(((MultiOperator)op).getKeys(), root, tx, op);
			long card = card(op.children().get(0));
			if (groupCard > card)
			{
				cCache.put(op, card);
				return card;
			}

			final long retval = groupCard;
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof NestedLoopJoinOperator)
		{
			HashSet<HashMap<Filter, Filter>> hshm = ((NestedLoopJoinOperator)op).getHSHM();
			double max = -1;
			for (HashMap<Filter, Filter> hm : hshm)
			{
				double temp = meta.likelihood(new ArrayList<Filter>(hm.keySet()), tx, op);
				if (temp > max)
				{
					max = temp;
				}
			}
			final long retval = (long)(card(op.children().get(0)) * card(op.children().get(1)) * max);
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof NetworkReceiveOperator)
		{
			long retval = 0;
			for (final Operator o : op.children())
			{
				retval += card(o);
			}

			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof NetworkHashAndSendOperator)
		{
			final long retval = card(op.children().get(0)) / ((NetworkHashAndSendOperator)op).parents().size();
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof NetworkSendRROperator)
		{
			final long retval = card(op.children().get(0)) / ((NetworkSendRROperator)op).parents().size();
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof NetworkSendOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof ProductOperator)
		{
			final long retval = card(op.children().get(0)) * card(op.children().get(1));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof ProjectOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof RenameOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof ReorderOperator)
		{
			final long retval = card(op.children().get(0));
			return retval;
		}

		if (op instanceof RootOperator)
		{
			final long retval = card(op.children().get(0));
			return retval;
		}

		if (op instanceof SelectOperator)
		{
			final long retval = (long)(((SelectOperator)op).likelihood(root, tx) * card(op.children().get(0)));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof SemiJoinOperator)
		{
			final long retval = (long)(meta.likelihood(((SemiJoinOperator)op).getHSHM(), root, tx, op) * card(op.children().get(0)));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof SortOperator)
		{
			final long retval = card(op.children().get(0));
			return retval;
		}

		if (op instanceof SubstringOperator)
		{
			final long retval = card(op.children().get(0));
			return retval;
		}

		if (op instanceof TopOperator)
		{
			final long retval = ((TopOperator)op).getRemaining();
			final long retval2 = card(op.children().get(0));

			if (retval2 < retval)
			{
				cCache.put(op, retval2);
				return retval2;
			}
			else
			{
				cCache.put(op, retval);
				return retval;
			}
		}

		if (op instanceof UnionOperator)
		{
			long retval = 0;
			for (final Operator o : op.children())
			{
				retval += card(o);
			}

			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof YearOperator)
		{
			final long retval = card(op.children().get(0));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof TableScanOperator)
		{
			final HashSet<HashMap<Filter, Filter>> hshm = ((TableScanOperator)op).getHSHM();
			if (hshm != null)
			{
				final long retval = (long)(meta.getTableCard(((TableScanOperator)op).getSchema(), ((TableScanOperator)op).getTable(), tx) * meta.likelihood(hshm, root, tx, op) * (1.0 / ((TableScanOperator)op).getNumNodes()));
				cCache.put(op, retval);
				return retval;
			}

			final long retval = (long)((1.0 / ((TableScanOperator)op).getNumNodes()) * meta.getTableCard(((TableScanOperator)op).getSchema(), ((TableScanOperator)op).getTable(), tx));
			cCache.put(op, retval);
			return retval;
		}

		if (op instanceof DummyOperator)
		{
			return 1;
		}

		if (op instanceof DEMOperator)
		{
			return 0;
		}

		HRDBMSWorker.logger.error("Unknown operator in card() in Phase4: " + op.getClass());
		throw new Exception("Unknown operator in card() in Phase4: " + op.getClass());
	}

	public void optimize() throws Exception
	{
		allUnionsHave2Children(root, new HashSet<Operator>());
		allIntersectionsHave2Children(root, new HashSet<Operator>());
		pushUpReceives();
		redistributeSorts();
		removeLocalSendReceive(root, new HashSet<Operator>());
		removeDuplicateReorders(root, new HashSet<Operator>());
		// HRDBMSWorker.logger.debug("Before removing hashes");
		// Phase1.printTree(root, 0);
		removeUnneededHash();
		// HRDBMSWorker.logger.debug("After removing unneeded hashing");
		// Phase1.printTree(root, 0);
		clearOpParents(root, new HashSet<Operator>());
		cleanupOrderedFilters(root, new HashSet<Operator>());
		// HRDBMSWorker.logger.debug("Exiting P4:");
		// Phase1.printTree(root, 0);
		// sanityCheck(root, -1);
	}

	private void allIntersectionsHave2Children(Operator op, HashSet<Operator> touched) throws Exception
	{
		if (touched.contains(op))
		{
			return;
		}

		if (!(op instanceof IntersectOperator))
		{
			touched.add(op);
			for (Operator o : op.children())
			{
				allIntersectionsHave2Children(o, touched);
			}
		}
		else
		{
			touched.add(op);
			if (op.children().size() <= 2)
			{
				for (Operator o : op.children())
				{
					allIntersectionsHave2Children(o, touched);
				}
			}
			else
			{
				ArrayList<Operator> remainder = new ArrayList<Operator>();
				int i = 0;
				for (Operator o : op.children())
				{
					if (i < 2)
					{
					}
					else
					{
						op.removeChild(o);
						remainder.add(o);
					}

					i++;
				}

				Operator parent = op.parent();
				parent.removeChild(op);
				Operator orig = op;
				while (remainder.size() != 0)
				{
					Operator newOp = orig.clone();
					newOp.add(op);
					newOp.add(remainder.remove(0));
					op = newOp;
				}

				parent.add(op);

				for (Operator o : orig.children())
				{
					allIntersectionsHave2Children(o, touched);
				}
			}
		}
	}

	private void allUnionsHave2Children(Operator op, HashSet<Operator> touched) throws Exception
	{
		if (touched.contains(op))
		{
			return;
		}

		if (!(op instanceof UnionOperator))
		{
			touched.add(op);
			for (Operator o : op.children())
			{
				allUnionsHave2Children(o, touched);
			}
		}
		else
		{
			touched.add(op);
			if (op.children().size() <= 2)
			{
				for (Operator o : op.children())
				{
					allUnionsHave2Children(o, touched);
				}
			}
			else
			{
				ArrayList<Operator> remainder = new ArrayList<Operator>();
				int i = 0;
				for (Operator o : op.children())
				{
					if (i < 2)
					{
					}
					else
					{
						op.removeChild(o);
						remainder.add(o);
					}

					i++;
				}

				Operator parent = op.parent();
				parent.removeChild(op);
				Operator orig = op;
				while (remainder.size() != 0)
				{
					Operator newOp = orig.clone();
					newOp.add(op);
					newOp.add(remainder.remove(0));
					op = newOp;
				}

				parent.add(op);

				for (Operator o : orig.children())
				{
					allUnionsHave2Children(o, touched);
				}
			}
		}
	}

	private void cleanupOrderedFilters(Operator op, HashSet<Operator> touched)
	{
		if (touched.contains(op))
		{
			return;
		}

		if (op instanceof TableScanOperator)
		{
			touched.add(op);
			((TableScanOperator)op).cleanupOrderedFilters();
		}
		else
		{
			touched.add(op);
			for (final Operator o : op.children())
			{
				cleanupOrderedFilters(o, touched);
			}
		}
	}

	private Operator cloneTree(Operator op, int level) throws Exception
	{
		final Operator clone = op.clone();
		if (level == 0)
		{
			for (final Operator o : op.children())
			{
				try
				{
					final Operator child = cloneTree(o, level + 1);
					clone.add(child);
					clone.setChildPos(op.getChildPos());
					if (o instanceof TableScanOperator)
					{
						final CNFFilter cnf = ((TableScanOperator)o).getCNFForParent(op);
						if (cnf != null)
						{
							((TableScanOperator)child).setCNFForParent(clone, cnf);
						}
					}
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		return clone;
	}

	private double concatPath(Operator op)
	{
		double retval = 0;
		int shift = 0;
		while (!(op instanceof RootOperator))
		{
			long i = 0;
			for (final Operator o : op.parent().children())
			{
				if (o == op)
				{
					retval += i * Math.pow(2.0, shift);
					shift += 20;
					break;
				}

				i++;
			}

			op = op.parent();
		}

		return retval;
	}

	private boolean doHashAnti(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		final Operator grandParent = parent.parent();
		// card(parent.children().get(0));
		// card(parent.children().get(1));
		ArrayList<String> join = ((AntiJoinOperator)parent).getJoinForChild(receive);
		if (join == null)
		{
			return false;
		}
		Operator other = null;
		for (final Operator o : parent.children())
		{
			if (o != receive)
			{
				other = o;
			}
		}
		join = ((AntiJoinOperator)parent).getJoinForChild(other);
		if (join == null)
		{
			return false;
		}
		join = ((AntiJoinOperator)parent).getJoinForChild(receive);
		verify2ReceivesForHash(parent);
		for (final Operator o : parent.children())
		{
			if (o != receive)
			{
				other = o;
			}
		}

		parent.removeChild(receive);
		grandParent.removeChild(parent);
		ArrayList<Operator> sends = new ArrayList<Operator>(receive.children().size());
		int starting = getStartingNode(MetaData.numWorkerNodes);
		int ID = id.getAndIncrement();

		CNFFilter cnf = null;
		for (final Operator child : (ArrayList<Operator>)receive.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);

			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(join, MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		int i = starting;
		ArrayList<Operator> receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<Operator> parents = new ArrayList<Operator>(receives.size());
		for (final Operator receive2 : receives)
		{
			final Operator clone = cloneTree(parent, 0);
			try
			{
				clone.add(receive2);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			clone.setNode(receive2.getNode());
			parents.add(clone);
			for (final Operator o : (ArrayList<Operator>)clone.children().clone())
			{
				if (!o.equals(receive2))
				{
					clone.removeChild(o);
				}
			}
		}

		Operator otherChild = null;
		for (final Operator child : parent.children())
		{
			if (!child.equals(receive))
			{
				otherChild = child;
			}
		}

		join = ((AntiJoinOperator)parent).getJoinForChild(otherChild);
		sends = new ArrayList<Operator>(otherChild.children().size());
		ID = id.getAndIncrement();
		for (final Operator child : (ArrayList<Operator>)otherChild.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);
			cnf = null;
			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(join, MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		i = starting;
		receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(parents.size());
		for (final Operator clone : parents)
		{
			for (final Operator receive2 : receives)
			{
				if (clone.getNode() == receive2.getNode())
				{
					try
					{
						clone.add(receive2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
			}

			final NetworkSendOperator send = new NetworkSendOperator(clone.getNode(), meta);
			sends2.add(send);
			try
			{
				send.add(clone);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
		r.setNode(grandParent.getNode());
		for (final NetworkSendOperator send : sends2)
		{
			try
			{
				r.add(send);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}
		try
		{
			grandParent.add(r);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		makeHierarchical2(r);
		makeHierarchical(r);
		cCache.clear();
		return false;
	}

	private boolean doHashSemi(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		final Operator grandParent = parent.parent();
		// card(parent.children().get(0));
		// card(parent.children().get(1));
		ArrayList<String> join = ((SemiJoinOperator)parent).getJoinForChild(receive);
		if (join == null)
		{
			return false;
		}
		Operator other = null;
		for (final Operator o : parent.children())
		{
			if (o != receive)
			{
				other = o;
			}
		}
		join = ((SemiJoinOperator)parent).getJoinForChild(other);
		if (join == null)
		{
			return false;
		}
		join = ((SemiJoinOperator)parent).getJoinForChild(receive);
		verify2ReceivesForHash(parent);
		for (final Operator o : parent.children())
		{
			if (o != receive)
			{
				other = o;
			}
		}

		parent.removeChild(receive);
		grandParent.removeChild(parent);
		ArrayList<Operator> sends = new ArrayList<Operator>(receive.children().size());
		int starting = getStartingNode(MetaData.numWorkerNodes);
		int ID = id.getAndIncrement();

		CNFFilter cnf = null;
		for (final Operator child : (ArrayList<Operator>)receive.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);

			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(join, MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		int i = starting;
		ArrayList<Operator> receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<Operator> parents = new ArrayList<Operator>(receives.size());
		for (final Operator receive2 : receives)
		{
			final Operator clone = cloneTree(parent, 0);
			try
			{
				clone.add(receive2);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			clone.setNode(receive2.getNode());
			parents.add(clone);
			for (final Operator o : (ArrayList<Operator>)clone.children().clone())
			{
				if (!o.equals(receive2))
				{
					clone.removeChild(o);
				}
			}
		}

		Operator otherChild = null;
		for (final Operator child : parent.children())
		{
			if (!child.equals(receive))
			{
				otherChild = child;
			}
		}

		join = ((SemiJoinOperator)parent).getJoinForChild(otherChild);
		sends = new ArrayList<Operator>(otherChild.children().size());
		ID = id.getAndIncrement();
		for (final Operator child : (ArrayList<Operator>)otherChild.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);
			cnf = null;
			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(join, MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		i = starting;
		receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(parents.size());
		for (final Operator clone : parents)
		{
			for (final Operator receive2 : receives)
			{
				if (clone.getNode() == receive2.getNode())
				{
					try
					{
						clone.add(receive2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
			}

			final NetworkSendOperator send = new NetworkSendOperator(clone.getNode(), meta);
			sends2.add(send);
			try
			{
				send.add(clone);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
		r.setNode(grandParent.getNode());
		for (final NetworkSendOperator send : sends2)
		{
			try
			{
				r.add(send);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}
		try
		{
			grandParent.add(r);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		makeHierarchical2(r);
		makeHierarchical(r);
		cCache.clear();
		return false;
	}

	private boolean doNonHashSemi(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		Operator right = parent.children().get(0);
		Operator left = parent.children().get(1);

		if (isAllAny(left, new HashSet<Operator>()) && left.children().size() == 1)
		{
			verify2ReceivesForSemi(parent);
			right = parent.children().get(0);
			left = parent.children().get(1);
			final Operator grandParent = parent.parent();
			parent.removeChild(left);
			grandParent.removeChild(parent);
			final ArrayList<Operator> grandChildren = new ArrayList<Operator>();
			for (final Operator child : (ArrayList<Operator>)right.children().clone())
			{
				final Operator grandChild = child.children().get(0);
				child.removeChild(grandChild);
				if (!(grandChild instanceof NetworkReceiveOperator))
				{
					grandChildren.add(grandChild);
				}
				else
				{
					grandChildren.addAll(getGrandChildren(grandChild));
				}
			}

			for (Operator o : grandChildren)
			{
				if (o.parent() != null)
				{
					o.parent().removeChild(o);
				}
			}

			final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(grandChildren.size());
			for (final Operator grandChild : grandChildren)
			{
				final Operator clone = cloneTree(parent, 0);
				clone.setNode(grandChild.getNode());
				final Operator leftClone = fullyCloneTree(left.children().get(0).children().get(0));
				setNodeForTree(leftClone, grandChild.getNode(), new HashSet<Operator>());
				try
				{
					clone.add(leftClone);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}

				for (final Operator o : (ArrayList<Operator>)clone.children().clone())
				{
					if (!o.equals(leftClone))
					{
						clone.removeChild(o);
					}
				}

				try
				{
					clone.add(grandChild);
					if (grandChild instanceof TableScanOperator)
					{
						final CNFFilter cnf = ((TableScanOperator)grandChild).getFirstCNF();
						if (cnf != null)
						{
							((TableScanOperator)grandChild).setCNFForParent(clone, cnf);
						}
					}
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}

				final NetworkSendOperator send2 = new NetworkSendOperator(clone.getNode(), meta);
				try
				{
					send2.add(clone);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}

				sends2.add(send2);
			}

			final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
			r.setNode(grandParent.getNode());

			for (final NetworkSendOperator send : sends2)
			{
				try
				{
					r.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}

			try
			{
				grandParent.add(r);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}

			makeHierarchical(r);
			cCache.clear();
			return false;
		}

		return false;
	}

	private void doSortRedistribution(SortOperator op, long card) throws Exception
	{
		final long numNodes = card / MAX_LOCAL_SORT;
		final int starting = getStartingNode(numNodes);
		final Operator parent = op.parent();
		parent.removeChild(op);
		final Operator child = op.children().get(0);
		op.removeChild(child);
		final int ID = id.getAndIncrement();
		final NetworkSendRROperator rr = new NetworkSendRROperator(ID, meta);
		try
		{
			rr.add(child);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		rr.setNode(child.getNode());

		final ArrayList<NetworkSendOperator> sends = new ArrayList<NetworkSendOperator>();
		int i = 0;
		while (i < numNodes && starting + i < MetaData.numWorkerNodes)
		{
			try
			{
				final NetworkHashReceiveOperator receive = new NetworkHashReceiveOperator(ID, meta);
				receive.setNode(starting + i);
				receive.add(rr);
				final SortOperator sort2 = op.clone();
				sort2.add(receive);
				sort2.setNode(starting + i);
				final NetworkSendOperator send = new NetworkSendOperator(starting + i, meta);
				send.add(sort2);
				sends.add(send);
				i++;
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		final NetworkReceiveAndMergeOperator receive = new NetworkReceiveAndMergeOperator((ArrayList<String>)op.getKeys().clone(), (ArrayList<Boolean>)op.getOrders().clone(), meta);
		receive.setNode(parent.getNode());
		for (final NetworkSendOperator send : sends)
		{
			receive.add(send);
		}

		try
		{
			parent.add(receive);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}

		if (parent instanceof TopOperator)
		{
			handleTop(receive);
		}
		makeHierarchical(receive);
		cCache.clear();
	}

	private Operator fullyCloneTree(Operator op) throws Exception
	{
		final Operator clone = op.clone();

		for (final Operator o : op.children())
		{
			try
			{
				final Operator child = fullyCloneTree(o);
				clone.add(child);
				clone.setChildPos(op.getChildPos());
				if (o instanceof TableScanOperator)
				{
					final CNFFilter cnf = ((TableScanOperator)o).getCNFForParent(op);
					if (cnf != null)
					{
						((TableScanOperator)child).setCNFForParent(clone, cnf);
					}
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		return clone;
	}

	private ArrayList<Operator> getGrandChildren(Operator op)
	{
		final ArrayList<Operator> retval = new ArrayList<Operator>();
		for (final Operator o : (ArrayList<Operator>)op.children().clone())
		{
			final Operator grandChild = o.children().get(0);
			if (!(grandChild instanceof NetworkReceiveOperator))
			{
				retval.add(grandChild);
			}
			else
			{
				retval.addAll(getGrandChildren(grandChild));
			}
		}

		return retval;
	}

	private SortOperator getLocalSort(Operator op) throws Exception
	{
		SortOperator retval = null;
		if (op instanceof SortOperator)
		{
			if (op.getNode() == -1)
			{
				if (retval == null)
				{
					retval = (SortOperator)op;
				}
				else
				{
					// I believe more than 1 is not possible
					Exception e = new Exception("Found more than 1 sort on the coord node!");
					HRDBMSWorker.logger.error("Found more than 1 sort on the coord node!", e);
					throw e;
				}
			}
		}

		if (op.getNode() == -1)
		{
			for (final Operator o : op.children())
			{
				final SortOperator s = getLocalSort(o);
				if (s != null)
				{
					if (retval == null)
					{
						retval = s;
					}
					else
					{
						Exception e = new Exception("Found more than 1 sort on the coord node!");
						HRDBMSWorker.logger.error("Found more than 1 sort on the coord node!", e);
						throw e;
					}
				}
			}
		}

		return retval;
	}

	private HashMap<NetworkReceiveOperator, Integer> getReceives(Operator op, int level)
	{
		final HashMap<NetworkReceiveOperator, Integer> retval = new HashMap<NetworkReceiveOperator, Integer>();
		if (!(op instanceof NetworkReceiveOperator))
		{
			if (op.getNode() == -1)
			{
				for (final Operator child : op.children())
				{
					retval.putAll(getReceives(child, level + 1));
				}
			}

			return retval;
		}
		else
		{
			if (op.getNode() == -1)
			{
				retval.put((NetworkReceiveOperator)op, level);
				for (final Operator child : op.children())
				{
					retval.putAll(getReceives(child, level + 1));
				}
			}

			return retval;
		}
	}

	private int getStartingNode(long numNodes) throws Exception
	{
		if (numNodes >= MetaData.numWorkerNodes)
		{
			return 0;
		}

		final int range = (int)(MetaData.numWorkerNodes - numNodes);
		if (range < 0)
		{
			return 0;
		}
		return (int)(Math.random() * range);
	}

	private ArrayList<TableScanOperator> getTables(Operator op, HashSet<Operator> touched)
	{
		if (touched.contains(op))
		{
			return new ArrayList<TableScanOperator>();
		}

		touched.add(op);
		if (op instanceof TableScanOperator)
		{
			ArrayList<TableScanOperator> retval = new ArrayList<TableScanOperator>();
			retval.add((TableScanOperator)op);
			return retval;
		}

		if (op.children().size() == 1)
		{
			return getTables(op.children().get(0), touched);
		}

		ArrayList<TableScanOperator> retval = new ArrayList<TableScanOperator>();
		for (Operator o : op.children())
		{
			retval.addAll(getTables(o, touched));
		}

		return retval;
	}

	private boolean handleExcept(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		verify2ReceivesForHash(parent);
		final Operator grandParent = parent.parent();
		// card(parent.children().get(0));
		// card(parent.children().get(1));
		parent.removeChild(receive);
		grandParent.removeChild(parent);
		ArrayList<Operator> sends = new ArrayList<Operator>(receive.children().size());
		int starting = getStartingNode(MetaData.numWorkerNodes);
		int ID = id.getAndIncrement();
		for (final Operator o : parent.children())
		{
			if (o != receive)
			{
			}
		}

		CNFFilter cnf = null;
		for (final Operator child : (ArrayList<Operator>)receive.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);

			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(new ArrayList<String>(grandChild.getPos2Col().values()), MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		int i = starting;
		ArrayList<Operator> receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<Operator> parents = new ArrayList<Operator>(receives.size());
		for (final Operator receive2 : receives)
		{
			final Operator clone = cloneTree(parent, 0);
			try
			{
				clone.add(receive2);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			clone.setNode(receive2.getNode());
			parents.add(clone);
			for (final Operator o : (ArrayList<Operator>)clone.children().clone())
			{
				if (!o.equals(receive2))
				{
					clone.removeChild(o);
				}
			}
		}

		Operator otherChild = null;
		for (final Operator child : parent.children())
		{
			if (!child.equals(receive))
			{
				otherChild = child;
			}
		}

		sends = new ArrayList<Operator>(otherChild.children().size());
		ID = id.getAndIncrement();
		for (final Operator child : (ArrayList<Operator>)otherChild.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);
			cnf = null;
			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(new ArrayList<String>(grandChild.getPos2Col().values()), MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		i = starting;
		receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(parents.size());
		for (final Operator clone : parents)
		{
			for (final Operator receive2 : receives)
			{
				if (clone.getNode() == receive2.getNode())
				{
					try
					{
						clone.add(receive2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
			}

			final NetworkSendOperator send = new NetworkSendOperator(clone.getNode(), meta);
			sends2.add(send);
			try
			{
				send.add(clone);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
		r.setNode(grandParent.getNode());
		for (final NetworkSendOperator send : sends2)
		{
			try
			{
				r.add(send);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}
		try
		{
			grandParent.add(r);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		makeHierarchical2(r);
		makeHierarchical(r);
		cCache.clear();
		return false;
	}

	private boolean handleMulti(NetworkReceiveOperator receive) throws Exception
	{
		if (receive.children().size() == 1)
		{
			pushAcross(receive);
			return true;
		}

		if (receive.children().get(0).children().get(0) instanceof RenameOperator && receive.children().get(0).children().get(0).children().get(0) instanceof MultiOperator)
		{
			return false;
		}

		final MultiOperator parent = (MultiOperator)receive.parent();
		final long card = card(parent);
		if (card > MAX_CARD_BEFORE_HASH && parent.getKeys().size() > 0)
		{
			final ArrayList<String> cols2 = new ArrayList<String>(parent.getKeys());
			final int starting = getStartingNode(MetaData.numWorkerNodes);
			final int ID = Phase4.id.getAndIncrement();
			final ArrayList<NetworkHashAndSendOperator> sends = new ArrayList<NetworkHashAndSendOperator>(receive.children().size());
			for (Operator o : (ArrayList<Operator>)receive.children().clone())
			{
				final Operator temp = o.children().get(0);
				o.removeChild(temp);
				receive.removeChild(o);
				o = temp;
				CNFFilter cnf = null;
				if (o instanceof TableScanOperator)
				{
					cnf = ((TableScanOperator)o).getCNFForParent(receive);
				}

				final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(cols2, MetaData.numWorkerNodes, ID, starting, meta);
				try
				{
					send.add(o);
					if (cnf != null)
					{
						((TableScanOperator)o).setCNFForParent(send, cnf);
					}
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
				send.setNode(o.getNode());
				sends.add(send);
			}

			int i = 0;
			final ArrayList<NetworkHashReceiveOperator> receives = new ArrayList<NetworkHashReceiveOperator>();
			while (i < MetaData.numWorkerNodes)
			{
				final NetworkHashReceiveOperator hrec = new NetworkHashReceiveOperator(ID, meta);
				hrec.setNode(i + starting);
				for (final NetworkHashAndSendOperator send : sends)
				{
					try
					{
						hrec.add(send);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
				receives.add(hrec);
				i++;
			}

			final Operator grandParent = parent.parent();
			grandParent.removeChild(parent);
			parent.removeChild(receive);
			for (final NetworkHashReceiveOperator hrec : receives)
			{
				final MultiOperator clone = parent.clone();
				clone.setNode(hrec.getNode());
				try
				{
					clone.add(hrec);
					final NetworkSendOperator send = new NetworkSendOperator(hrec.getNode(), meta);
					send.add(clone);
					receive.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}

			try
			{
				grandParent.add(receive);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			cCache.clear();
			return true;
		}

		if (parent.existsCountDistinct())
		{
			if (parent.getKeys().size() == 0 && parent.getOutputCols().size() == 1)
			{
				String col = parent.getInputCols().get(0);
				for (Operator op : (ArrayList<Operator>)receive.children().clone())
				{
					Operator child = op.children().get(0);
					if (!(child instanceof UnionOperator))
					{
						op.removeChild(child);
						if (child.getCols2Pos().size() > 1)
						{
							ArrayList<String> cols = new ArrayList<String>();
							cols.add(col);
							Operator child2 = new ProjectOperator(cols, meta);
							child2.add(child);
							child = child2;
						}
					
						Operator child2 = new UnionOperator(true, meta);
						child2.add(child);
						child = child2;
						op.add(child);
						receive.removeChild(op);
						receive.add(op);
					}
				}
				
				parent.removeChild(receive);
				parent.add(receive);
			}
			return false;
		}

		final ArrayList<Operator> children = receive.children();
		final HashMap<Operator, Operator> send2Child = new HashMap<Operator, Operator>();
		final HashMap<Operator, CNFFilter> send2CNF = new HashMap<Operator, CNFFilter>();
		for (final Operator child : children)
		{
			send2Child.put(child, child.children().get(0));
			if (child.children().get(0) instanceof TableScanOperator)
			{
				final CNFFilter cnf = ((TableScanOperator)child.children().get(0)).getCNFForParent(child);
				if (cnf != null)
				{
					send2CNF.put(child, cnf);
				}
			}
			child.removeChild(child.children().get(0));
		}

		final int oldSuffix = Phase3.colSuffix;
		final Operator orig = parent.parent();
		MultiOperator pClone;
		final ArrayList<String> cols = new ArrayList<String>(parent.getPos2Col().values());
		ArrayList<String> oldCols = null;
		ArrayList<String> newCols = null;

		for (final Map.Entry entry : send2Child.entrySet())
		{
			pClone = parent.clone();
			if (pClone.getOutputCols().size() == 0)
			{
				pClone.addCount("_P" + Phase3.colSuffix++);
			}
			while (pClone.hasAvg())
			{
				final String avgCol = pClone.getAvgCol();
				final ArrayList<String> newCols2 = new ArrayList<String>(2);
				final String newCol1 = "_P" + Phase3.colSuffix++;
				final String newCol2 = "_P" + Phase3.colSuffix++;
				newCols2.add(newCol1);
				newCols2.add(newCol2);
				final HashMap<String, ArrayList<String>> old2News = new HashMap<String, ArrayList<String>>();
				old2News.put(avgCol, newCols2);
				pClone.replaceAvgWithSumAndCount(old2News);
				parent.replaceAvgWithSumAndCount(old2News);
				final Operator grandParent = parent.parent();
				grandParent.removeChild(parent);
				final ExtendOperator extend = new ExtendOperator("/," + old2News.get(avgCol).get(0) + "," + old2News.get(avgCol).get(1), avgCol, meta);
				try
				{
					extend.add(parent);
					extend.setNode(parent.getNode());
					grandParent.add(extend);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}

			Phase3.colSuffix = oldSuffix;
			oldCols = new ArrayList(pClone.getOutputCols());
			newCols = new ArrayList(pClone.getInputCols());
			final HashMap<String, String> old2New2 = new HashMap<String, String>();
			int counter = 10;
			int i = 0;
			for (final String col : oldCols)
			{
				if (!old2New2.containsValue(newCols.get(i)))
				{
					old2New2.put(col, newCols.get(i));
				}
				else
				{
					String new2 = newCols.get(i) + counter++;
					while (old2New2.containsValue(new2))
					{
						new2 = newCols.get(i) + counter++;
					}

					old2New2.put(col, new2);
				}

				i++;
			}
			newCols = new ArrayList<String>(oldCols.size());
			for (final String col : oldCols)
			{
				newCols.add(old2New2.get(col));
			}

			try
			{
				pClone.add((Operator)entry.getValue());
				pClone.setNode(((Operator)entry.getValue()).getNode());
				if (send2CNF.containsKey(entry.getKey()))
				{
					((TableScanOperator)entry.getValue()).setCNFForParent(pClone, send2CNF.get(entry.getKey()));
				}
				RenameOperator rename = null;
				rename = new RenameOperator(oldCols, newCols, meta);
				rename.add(pClone);
				rename.setNode(pClone.getNode());

				((Operator)entry.getKey()).add(rename);
				receive.removeChild((Operator)entry.getKey());
				receive.add((Operator)entry.getKey());
				parent.removeChild(receive);
				parent.add(receive);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		parent.changeCountsToSums();
		parent.updateInputColumns(oldCols, newCols);
		try
		{
			final Operator child = parent.children().get(0);
			parent.removeChild(child);
			parent.add(child);
			Operator grandParent = parent.parent();
			grandParent.removeChild(parent);
			grandParent.add(parent);
			while (!grandParent.equals(orig))
			{
				final Operator next = grandParent.parent();
				next.removeChild(grandParent);
				if (next.equals(orig))
				{
					final ReorderOperator order = new ReorderOperator(cols, meta);
					order.add(grandParent);
					order.setNode(grandParent.getNode());
					orig.add(order);
					grandParent = next;
				}
				else
				{
					next.add(grandParent);
					grandParent = next;
				}
			}
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		cCache.clear();
		return false;
	}

	private boolean handleSort(NetworkReceiveOperator receive) throws Exception
	{
		if (receive.children().size() == 1)
		{
			pushAcross(receive);
			return true;
		}
		final SortOperator parent = (SortOperator)receive.parent();
		final Operator grandParent = parent.parent();
		final ArrayList<Operator> children = (ArrayList<Operator>)receive.children().clone();
		final HashMap<Operator, Operator> send2Child = new HashMap<Operator, Operator>();
		final HashMap<Operator, CNFFilter> send2CNF = new HashMap<Operator, CNFFilter>();
		for (final Operator child : children)
		{
			send2Child.put(child, child.children().get(0));
			if (child.children().get(0) instanceof TableScanOperator)
			{
				final CNFFilter cnf = ((TableScanOperator)child.children().get(0)).getCNFForParent(child);
				if (cnf != null)
				{
					send2CNF.put(child, cnf);
				}
			}
			child.removeChild(child.children().get(0));
			receive.removeChild(child);
		}
		parent.removeChild(receive);
		grandParent.removeChild(parent);
		receive = new NetworkReceiveAndMergeOperator((ArrayList<String>)parent.getKeys().clone(), (ArrayList<Boolean>)parent.getOrders().clone(), meta);

		try
		{
			for (final Map.Entry entry : send2Child.entrySet())
			{
				final Operator pClone = parent.clone();
				pClone.add((Operator)entry.getValue());
				pClone.setNode(((Operator)entry.getValue()).getNode());
				if (send2CNF.containsKey(entry.getKey()))
				{
					((TableScanOperator)entry.getValue()).setCNFForParent(pClone, send2CNF.get(entry.getKey()));
				}
				((Operator)entry.getKey()).add(pClone);
				receive.add((Operator)entry.getKey());
			}
			grandParent.add(receive);
			receive.setNode(grandParent.getNode());
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}

		cCache.clear();
		return false;
	}

	private boolean handleTop(NetworkReceiveOperator receive) throws Exception
	{
		if (receive.children().size() == 1)
		{
			pushAcross(receive);
			return true;
		}
		final TopOperator parent = (TopOperator)receive.parent();
		final ArrayList<Operator> children = (ArrayList<Operator>)receive.children().clone();
		final HashMap<Operator, Operator> send2Child = new HashMap<Operator, Operator>();
		final HashMap<Operator, CNFFilter> send2CNF = new HashMap<Operator, CNFFilter>();
		for (final Operator child : children)
		{
			send2Child.put(child, child.children().get(0));
			if (child.children().get(0) instanceof TableScanOperator)
			{
				final CNFFilter cnf = ((TableScanOperator)child.children().get(0)).getCNFForParent(child);
				if (cnf != null)
				{
					send2CNF.put(child, cnf);
				}
			}
			child.removeChild(child.children().get(0));
		}

		for (final Map.Entry entry : send2Child.entrySet())
		{
			final Operator pClone = parent.clone();
			try
			{
				pClone.add((Operator)entry.getValue());
				pClone.setNode(((Operator)entry.getValue()).getNode());
				if (send2CNF.containsKey(entry.getKey()))
				{
					((TableScanOperator)entry.getValue()).setCNFForParent(pClone, send2CNF.get(entry.getKey()));
				}
				((Operator)entry.getKey()).add(pClone);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		cCache.clear();
		return false;
	}

	private boolean handleUnion(NetworkReceiveOperator receive) throws Exception
	{
		if (receive.parent().children().size() == 1)
		{
			DEMOperator dummy = new DEMOperator(meta);
			receive.parent().add(dummy);
			dummy.setNode(receive.parent().getNode());
			dummy.setCols2Pos(receive.parent().getCols2Pos());
			dummy.setPos2Col(receive.parent().getPos2Col());
			dummy.setCols2Types(receive.parent().getCols2Types());
		}

		final Operator parent = receive.parent();
		verify2ReceivesForHash(parent);
		final Operator grandParent = parent.parent();
		// card(parent.children().get(0));
		// card(parent.children().get(1));
		parent.removeChild(receive);
		grandParent.removeChild(parent);
		ArrayList<Operator> sends = new ArrayList<Operator>(receive.children().size());
		int starting = getStartingNode(MetaData.numWorkerNodes);
		int ID = id.getAndIncrement();
		for (final Operator o : parent.children())
		{
			if (o != receive)
			{
			}
		}

		CNFFilter cnf = null;
		for (final Operator child : (ArrayList<Operator>)receive.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);

			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(new ArrayList<String>(grandChild.getPos2Col().values()), MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		int i = starting;
		ArrayList<Operator> receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<Operator> parents = new ArrayList<Operator>(receives.size());
		for (final Operator receive2 : receives)
		{
			final Operator clone = cloneTree(parent, 0);
			try
			{
				clone.add(receive2);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			clone.setNode(receive2.getNode());
			parents.add(clone);
			for (final Operator o : (ArrayList<Operator>)clone.children().clone())
			{
				if (!o.equals(receive2))
				{
					clone.removeChild(o);
				}
			}
		}

		Operator otherChild = null;
		for (final Operator child : parent.children())
		{
			if (!child.equals(receive))
			{
				otherChild = child;
			}
		}

		sends = new ArrayList<Operator>(otherChild.children().size());
		ID = id.getAndIncrement();
		for (final Operator child : (ArrayList<Operator>)otherChild.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);
			cnf = null;
			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(new ArrayList<String>(grandChild.getPos2Col().values()), MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		i = starting;
		receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(parents.size());
		for (final Operator clone : parents)
		{
			for (final Operator receive2 : receives)
			{
				if (clone.getNode() == receive2.getNode())
				{
					try
					{
						clone.add(receive2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
			}

			final NetworkSendOperator send = new NetworkSendOperator(clone.getNode(), meta);
			sends2.add(send);
			try
			{
				send.add(clone);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
		r.setNode(grandParent.getNode());
		for (final NetworkSendOperator send : sends2)
		{
			try
			{
				r.add(send);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}
		try
		{
			grandParent.add(r);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		makeHierarchical2(r);
		makeHierarchical(r);
		cCache.clear();
		return false;
	}

	private boolean isAllAny(Operator op, HashSet<Operator> touched)
	{
		if (touched.contains(op))
		{
			return true;
		}

		if (op instanceof TableScanOperator)
		{
			touched.add(op);
			return ((TableScanOperator)op).anyNode();
		}
		else
		{
			touched.add(op);
			for (final Operator o : op.children())
			{
				if (!isAllAny(o, touched))
				{
					return false;
				}
			}

			return true;
		}
	}

	private void makeHierarchical(NetworkReceiveOperator receive) throws Exception
	{
		if (receive.children().size() > MAX_INCOMING_CONNECTIONS)
		{
			int numMiddle = receive.children().size() / MAX_INCOMING_CONNECTIONS;
			if (receive.children().size() % MAX_INCOMING_CONNECTIONS != 0)
			{
				numMiddle++;
			}
			int numPerMiddle = receive.children().size() / numMiddle;
			if (receive.children().size() % numMiddle != 0)
			{
				numPerMiddle++;
			}

			final ArrayList<Operator> sends = (ArrayList<Operator>)receive.children().clone();
			for (final Operator send : sends)
			{
				receive.removeChild(send);
			}

			NetworkReceiveOperator newReceive = null;
			if (receive instanceof NetworkReceiveAndMergeOperator)
			{
				newReceive = receive.clone();
			}
			else
			{
				newReceive = new NetworkReceiveOperator(meta);
			}

			int i = 0;
			while (sends.size() > 0)
			{
				try
				{
					newReceive.add(sends.get(0));
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
				sends.remove(0);
				i++;

				if (i == numPerMiddle)
				{
					int node = Math.abs(ThreadLocalRandom.current().nextInt()) % MetaData.numWorkerNodes;
					final NetworkSendOperator newSend = new NetworkSendOperator(node, meta);
					try
					{
						newSend.add(newReceive);
						newReceive.setNode(newSend.getNode());
						receive.add(newSend);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
					newReceive = new NetworkReceiveOperator(meta);
					i = 0;
				}
			}
			makeHierarchical(receive);
		}
	}

	private void makeHierarchical2(NetworkReceiveOperator op) throws Exception
	{
		// NetworkHashAndSend -> NetworkHashReceive
		ArrayList<NetworkHashReceiveOperator> lreceives = new ArrayList<NetworkHashReceiveOperator>();
		ArrayList<NetworkReceiveOperator> lreceives2 = new ArrayList<NetworkReceiveOperator>();
		ArrayList<NetworkHashReceiveOperator> rreceives = new ArrayList<NetworkHashReceiveOperator>();
		ArrayList<NetworkReceiveOperator> rreceives2 = new ArrayList<NetworkReceiveOperator>();
		ArrayList<NetworkHashAndSendOperator> lsends2 = new ArrayList<NetworkHashAndSendOperator>();
		ArrayList<NetworkHashAndSendOperator> rsends2 = new ArrayList<NetworkHashAndSendOperator>();
		int lstart = 0;
		int rstart = 0;
		for (final Operator child : op.children())
		{
			if (child.children().get(0).children().get(0) instanceof NetworkHashReceiveOperator)
			{
				lreceives.add((NetworkHashReceiveOperator)child.children().get(0).children().get(0));
			}
			if (child.children().size() > 1)
			{
				if (child.children().get(0).children().get(1) instanceof NetworkHashReceiveOperator)
				{
					rreceives.add((NetworkHashReceiveOperator)child.children().get(0).children().get(1));
				}
			}
		}

		boolean dol = true;
		boolean dor = true;
		if (lreceives.size() == 0)
		{
			dol = false;
		}
		if (rreceives.size() == 0)
		{
			dor = false;
		}

		final ArrayList<NetworkHashAndSendOperator> lsends = new ArrayList<NetworkHashAndSendOperator>(lreceives.get(0).children().size());
		if (dol)
		{
			for (final Operator o : lreceives.get(0).children())
			{
				lsends.add((NetworkHashAndSendOperator)o);
			}
		}

		final ArrayList<NetworkHashAndSendOperator> rsends = new ArrayList<NetworkHashAndSendOperator>();
		if (dor)
		{
			for (final Operator o : rreceives.get(0).children())
			{
				rsends.add((NetworkHashAndSendOperator)o);
			}
		}

		if (dol)
		{
			if (lsends.size() > Phase3.MAX_INCOMING_CONNECTIONS)
			{
				int numMiddle = Phase3.MAX_INCOMING_CONNECTIONS;
				int lstarting = getStartingNode(numMiddle);
				lstart = lstarting;
				int numPerMiddle = lsends.size() / numMiddle;
				if (lsends.size() % numMiddle != 0)
				{
					numPerMiddle++;
				}

				int count = 0;
				NetworkReceiveOperator current = new NetworkReceiveOperator(meta);
				lreceives2.add(current);
				current.setNode(lstarting);
				for (NetworkHashAndSendOperator send : lsends)
				{
					Operator child = send.children().get(0);
					send.removeChild(child);
					NetworkSendOperator newSend = new NetworkSendOperator(child.getNode(), meta);
					newSend.add(child);
					current.add(newSend);
					count++;

					if (count == numPerMiddle)
					{
						lstarting++;
						current = new NetworkReceiveOperator(meta);
						lreceives2.add(current);
					}
				}
			}
		}

		if (dor)
		{
			if (rsends.size() > Phase3.MAX_INCOMING_CONNECTIONS)
			{
				int numMiddle = Phase3.MAX_INCOMING_CONNECTIONS;
				int rstarting = getStartingNode(numMiddle);
				rstart = rstarting;
				int numPerMiddle = rsends.size() / numMiddle;
				if (rsends.size() % numMiddle != 0)
				{
					numPerMiddle++;
				}

				int count = 0;
				NetworkReceiveOperator current = new NetworkReceiveOperator(meta);
				rreceives2.add(current);
				current.setNode(rstarting);
				for (NetworkHashAndSendOperator send : rsends)
				{
					Operator child = send.children().get(0);
					send.removeChild(child);
					NetworkSendOperator newSend = new NetworkSendOperator(child.getNode(), meta);
					newSend.add(child);
					current.add(newSend);
					count++;

					if (count == numPerMiddle)
					{
						rstarting++;
						current = new NetworkReceiveOperator(meta);
						rreceives2.add(current);
					}
				}
			}
		}

		if (lreceives2.size() > 0)
		{
			for (NetworkReceiveOperator r : lreceives2)
			{
				makeHierarchical(r);
				NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(lsends.get(0).getHashCols(), lreceives.size(), lsends.get(0).getID(), lstart, meta);
				send.add(r);
				send.setNode(r.getNode());
				lsends2.add(send);
			}
		}

		if (rreceives2.size() > 0)
		{
			for (NetworkReceiveOperator r : rreceives2)
			{
				makeHierarchical(r);
				NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(rsends.get(0).getHashCols(), rreceives.size(), rsends.get(0).getID(), rstart, meta);
				send.add(r);
				send.setNode(r.getNode());
				rsends2.add(send);
			}
		}

		if (lsends2.size() > 0)
		{
			for (NetworkHashReceiveOperator r : lreceives)
			{
				Operator parent = r.parent();
				parent.removeChild(r);
				NetworkHashReceiveOperator newReceive = r.clone();
				for (NetworkHashAndSendOperator s : lsends2)
				{
					newReceive.add(s);
				}

				parent.add(newReceive);
				newReceive.setNode(parent.getNode());
			}
		}

		if (rsends2.size() > 0)
		{
			for (NetworkHashReceiveOperator r : rreceives)
			{
				Operator parent = r.parent();
				parent.removeChild(r);
				NetworkHashReceiveOperator newReceive = r.clone();
				for (NetworkHashAndSendOperator s : rsends2)
				{
					newReceive.add(s);
				}

				parent.add(newReceive);
				newReceive.setNode(parent.getNode());
			}
		}

		cCache.clear();
	}

	private boolean noLargeUpstreamJoins(Operator op) throws Exception
	{
		Operator o = op.parent();
		while (!(o instanceof RootOperator))
		{
			if (o instanceof ProductOperator)
			{
				long l = card(o.children().get(0));
				long r = card(o.children().get(1));
				if (l == 0)
				{
					l = 1;
				}

				if (r == 0)
				{
					r = 1;
				}
				if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(o))
				{
					return true;
				}

				return false;
			}

			if (o instanceof HashJoinOperator)
			{
				if (card(o.children().get(0)) + card(o.children().get(1)) <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(o))
				{
					return true;
				}

				return false;
			}

			if (o instanceof NestedLoopJoinOperator)
			{
				long l = card(o.children().get(0));
				long r = card(o.children().get(1));
				if (l == 0)
				{
					l = 1;
				}

				if (r == 0)
				{
					r = 1;
				}
				if (((NestedLoopJoinOperator)o).usesHash() && l + r <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(o))
				{
					return true;
				}
				else if (((NestedLoopJoinOperator)o).usesSort() && card(o) <= MAX_LOCAL_NO_HASH_PRODUCT && r <= MAX_LOCAL_SORT && noLargeUpstreamJoins(o))
				{
					return true;
				}
				else if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(o))
				{
					return true;
				}

				return false;
			}

			if (o instanceof SemiJoinOperator)
			{
				long l = card(o.children().get(0));
				long r = card(o.children().get(1));
				if (l == 0)
				{
					l = 1;
				}

				if (r == 0)
				{
					r = 1;
				}
				if (((SemiJoinOperator)o).usesHash() && l + r <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(o))
				{
					return true;
				}
				else if (((SemiJoinOperator)o).usesSort() && card(o) <= MAX_LOCAL_NO_HASH_PRODUCT && r <= MAX_LOCAL_SORT && noLargeUpstreamJoins(o))
				{
					return true;
				}
				else if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(o))
				{
					return true;
				}

				return false;
			}

			if (o instanceof AntiJoinOperator)
			{
				long l = card(o.children().get(0));
				long r = card(o.children().get(1));
				if (l == 0)
				{
					l = 1;
				}

				if (r == 0)
				{
					r = 1;
				}
				if (((AntiJoinOperator)o).usesHash() && l + r <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(o))
				{
					return true;
				}
				else if (((AntiJoinOperator)o).usesSort() && card(o) <= MAX_LOCAL_NO_HASH_PRODUCT && r <= MAX_LOCAL_SORT && noLargeUpstreamJoins(o))
				{
					return true;
				}
				else if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(o))
				{
					return true;
				}

				return false;
			}

			o = o.parent();
		}

		return true;
	}

	private ArrayList<NetworkReceiveOperator> order(HashMap<NetworkReceiveOperator, Integer> receives)
	{
		final ArrayList<NetworkReceiveOperator> retval = new ArrayList<NetworkReceiveOperator>(receives.size());
		while (receives.size() > 0)
		{
			NetworkReceiveOperator maxReceive = null;
			int maxLevel = Integer.MIN_VALUE;
			double minConcatPath = Double.MAX_VALUE;
			for (final Map.Entry entry : receives.entrySet())
			{
				if ((Integer)entry.getValue() > maxLevel)
				{
					maxLevel = (Integer)entry.getValue();
					maxReceive = (NetworkReceiveOperator)entry.getKey();
					minConcatPath = concatPath((Operator)entry.getKey());
				}
				else if (lt)
				{
					if ((Integer)entry.getValue() == maxLevel && concatPath((Operator)entry.getKey()) < minConcatPath)
					{
						maxLevel = (Integer)entry.getValue();
						maxReceive = (NetworkReceiveOperator)entry.getKey();
						minConcatPath = concatPath((Operator)entry.getKey());
					}
				}
				else
				{
					if ((Integer)entry.getValue() == maxLevel && concatPath((Operator)entry.getKey()) > minConcatPath)
					{
						maxLevel = (Integer)entry.getValue();
						maxReceive = (NetworkReceiveOperator)entry.getKey();
						minConcatPath = concatPath((Operator)entry.getKey());
					}
				}
			}

			receives.remove(maxReceive);
			retval.add(maxReceive);
		}

		lt = !lt;
		return retval;
	}

	private void pushAcross(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		final Operator grandParent = parent.parent();
		final ArrayList<Operator> children = receive.children();
		final HashMap<Operator, Operator> send2Child = new HashMap<Operator, Operator>();
		final HashMap<Operator, CNFFilter> send2CNF = new HashMap<Operator, CNFFilter>();
		for (final Operator child : children)
		{
			send2Child.put(child, child.children().get(0));
			if (child.children().get(0) instanceof TableScanOperator)
			{
				final CNFFilter cnf = ((TableScanOperator)child.children().get(0)).getCNFForParent(child);
				if (cnf != null)
				{
					send2CNF.put(child, cnf);
				}
			}
			child.removeChild(child.children().get(0));
		}
		parent.removeChild(receive);
		grandParent.removeChild(parent);

		try
		{
			for (final Map.Entry entry : send2Child.entrySet())
			{
				final Operator pClone = parent.clone();
				pClone.add((Operator)entry.getValue());
				pClone.setNode(((Operator)entry.getValue()).getNode());
				if (send2CNF.containsKey(entry.getKey()))
				{
					((TableScanOperator)entry.getValue()).setCNFForParent(pClone, send2CNF.get(entry.getKey()));
				}
				((Operator)entry.getKey()).add(pClone);
				receive.removeChild((Operator)entry.getKey());
				receive.add((Operator)entry.getKey());
			}
			grandParent.add(receive);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		cCache.clear();
	}

	private void pushUpReceives() throws Exception
	{
		final HashSet<NetworkReceiveOperator> completed = new HashSet<NetworkReceiveOperator>();
		boolean workToDo = true;
		while (workToDo)
		{
			workToDo = false;
			final HashMap<NetworkReceiveOperator, Integer> receives = getReceives(root, 0);
			for (final NetworkReceiveOperator receive : order(receives))
			{
				if (completed.contains(receive))
				{
					continue;
				}
				if (!treeContains(root, receive))
				{
					continue;
				}

				final Operator op = receive.parent();
				if (op instanceof SelectOperator || op instanceof YearOperator || op instanceof SubstringOperator || op instanceof ProjectOperator || op instanceof ExtendOperator || op instanceof RenameOperator || op instanceof ReorderOperator || op instanceof CaseOperator || op instanceof ExtendObjectOperator || op instanceof DateMathOperator || op instanceof ConcatOperator)
				{
					pushAcross(receive);
					workToDo = true;
					break;
				}
				else if (op instanceof SortOperator)
				{
					// if (!eligible.contains(receive))
					// {
					// continue;
					// }
					if (!handleSort(receive))
					{
						completed.add(receive);
					}
					workToDo = true;
					break;
				}
				else if (op instanceof MultiOperator)
				{
					// if (!eligible.contains(receive))
					// {
					// continue;
					// }
					if (!handleMulti(receive))
					{
						completed.add(receive);
					}
					workToDo = true;
					break;
				}
				else if (op instanceof ProductOperator)
				{
					op.parent();
					// checkOrder(op);
					long l = card(op.children().get(0));
					long r = card(op.children().get(1));
					if (l == 0)
					{
						l = 1;
					}

					if (r == 0)
					{
						r = 1;
					}

					if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(op))
					{
						continue;
					}

					if (!redistributeProduct(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof HashJoinOperator)
				{
					op.parent();
					if (card(op.children().get(0)) + card(op.children().get(1)) <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(op))
					{
						continue;
					}

					if (!redistributeHash(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof UnionOperator)
				{
					op.parent();

					if (noLargeUpstreamJoins(op))
					{
						long card = 0;
						for (Operator child : op.children())
						{
							card += card(child);
						}

						if (card <= MAX_LOCAL_LEFT_HASH)
						{
							continue;
						}
					}

					if (!handleUnion(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof IntersectOperator)
				{
					op.parent();
					if (card(op.children().get(0)) + card(op.children().get(1)) <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(op))
					{
						continue;
					}

					if (!handleExcept(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof ExceptOperator)
				{
					op.parent();
					if (card(op.children().get(0)) + card(op.children().get(1)) <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(op))
					{
						continue;
					}

					if (!handleExcept(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof NestedLoopJoinOperator)
				{
					op.parent();
					// checkOrder(op);
					long l = card(op.children().get(0));
					long r = card(op.children().get(1));
					if (l == 0)
					{
						l = 1;
					}

					if (r == 0)
					{
						r = 1;
					}
					if (((NestedLoopJoinOperator)op).usesHash() && l + r <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(op))
					{
						continue;
					}
					else if (((NestedLoopJoinOperator)op).usesSort() && card(op) <= MAX_LOCAL_NO_HASH_PRODUCT && r <= MAX_LOCAL_SORT && noLargeUpstreamJoins(op))
					{
						continue;
					}
					else if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(op))
					{
						continue;
					}

					if (!redistributeNL(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof SemiJoinOperator)
				{
					long l = card(op.children().get(0));
					long r = card(op.children().get(1));
					if (l == 0)
					{
						l = 1;
					}

					if (r == 0)
					{
						r = 1;
					}
					op.parent();
					if (((SemiJoinOperator)op).usesHash() && l + r <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(op))
					{
						// System.out.println("SemiJoin uses partial hash, but is not pushed down because...");
						// System.out.println("Left card = " +
						// card(op.children().get(0)));
						// System.out.println("Right card = " +
						// card(op.children().get(1)));
						continue;
					}
					else if (((SemiJoinOperator)op).usesSort() && card(op) <= MAX_LOCAL_NO_HASH_PRODUCT && r <= MAX_LOCAL_SORT && noLargeUpstreamJoins(op))
					{
						// System.out.println("SemiJoin uses sort, but is not pushed down because...");
						// System.out.println("Card = " + card(op));
						continue;
					}
					else if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(op))
					{
						// System.out.println("SemiJoin uses NL, but is not pushed down because...");
						// System.out.println("Left card = " +
						// card(op.children().get(0)));
						// System.out.println("Right card = " +
						// card(op.children().get(1)));
						continue;
					}

					// System.out.println("SemiJoin is pushed down");
					if (!redistributeSemi(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof AntiJoinOperator)
				{
					long l = card(op.children().get(0));
					long r = card(op.children().get(1));
					if (l == 0)
					{
						l = 1;
					}

					if (r == 0)
					{
						r = 1;
					}
					op.parent();
					if (((AntiJoinOperator)op).usesHash() && l + r <= MAX_LOCAL_LEFT_HASH && noLargeUpstreamJoins(op))
					{
						// System.out.println("AntiJoin uses partial hash, but is not pushed down because...");
						// System.out.println("Left card = " +
						// card(op.children().get(0)));
						// System.out.println("Right card = " +
						// card(op.children().get(1)));
						continue;
					}
					else if (((AntiJoinOperator)op).usesSort() && card(op) <= MAX_LOCAL_NO_HASH_PRODUCT && r <= MAX_LOCAL_SORT && noLargeUpstreamJoins(op))
					{
						// System.out.println("AntiJoin uses sort, but is not pushed down because...");
						// System.out.println("Card = " + card(op));
						continue;
					}
					else if (l * r <= MAX_LOCAL_NO_HASH_PRODUCT && noLargeUpstreamJoins(op))
					{
						// System.out.println("AntiJoin uses NL, but is not pushed down because...");
						// System.out.println("Left card = " +
						// card(op.children().get(0)));
						// System.out.println("Right card = " +
						// card(op.children().get(1)));
						continue;
					}

					// System.out.println("AntiJoin is pushed down");
					if (!redistributeAnti(receive))
					{
						completed.add(receive);
					}

					workToDo = true;
					break;
				}
				else if (op instanceof TopOperator)
				{
					// if (!eligible.contains(receive))
					// {
					// continue;
					// }
					if (!handleTop(receive))
					{
						completed.add(receive);
					}
					workToDo = true;
					break;
				}
			}
		}
	}

	private boolean redistributeAnti(NetworkReceiveOperator receive) throws Exception
	{
		if (((AntiJoinOperator)receive.parent()).usesHash())
		{
			return doHashAnti(receive);
		}

		return doNonHashSemi(receive);
	}

	private boolean redistributeHash(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		final Operator grandParent = parent.parent();
		// card(parent.children().get(0));
		// card(parent.children().get(1));
		ArrayList<String> join = ((JoinOperator)parent).getJoinForChild(receive);
		verify2ReceivesForHash(parent);

		parent.removeChild(receive);
		grandParent.removeChild(parent);
		ArrayList<Operator> sends = new ArrayList<Operator>(receive.children().size());
		int starting = getStartingNode(MetaData.numWorkerNodes);
		int ID = id.getAndIncrement();

		CNFFilter cnf = null;
		for (final Operator child : (ArrayList<Operator>)receive.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);

			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(join, MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		int i = starting;
		ArrayList<Operator> receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<Operator> parents = new ArrayList<Operator>(receives.size());
		for (final Operator receive2 : receives)
		{
			final Operator clone = cloneTree(parent, 0);
			try
			{
				clone.add(receive2);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			clone.setNode(receive2.getNode());
			parents.add(clone);
			for (final Operator o : (ArrayList<Operator>)clone.children().clone())
			{
				if (!o.equals(receive2))
				{
					clone.removeChild(o);
				}
			}
		}

		Operator otherChild = null;
		for (final Operator child : parent.children())
		{
			if (!child.equals(receive))
			{
				otherChild = child;
			}
		}

		join = ((JoinOperator)parent).getJoinForChild(otherChild);
		sends = new ArrayList<Operator>(otherChild.children().size());
		ID = id.getAndIncrement();
		for (final Operator child : (ArrayList<Operator>)otherChild.children().clone())
		{
			final int node = child.getNode();
			final Operator grandChild = child.children().get(0);
			cnf = null;
			if (grandChild instanceof TableScanOperator)
			{
				cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
			}
			child.removeChild(grandChild);

			final NetworkHashAndSendOperator send = new NetworkHashAndSendOperator(join, MetaData.numWorkerNodes, ID, starting, meta);
			try
			{
				send.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(send, cnf);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
			send.setNode(node);
			sends.add(send);
		}

		i = starting;
		receives = new ArrayList<Operator>();
		while (i < MetaData.numWorkerNodes)
		{
			final NetworkHashReceiveOperator receive2 = new NetworkHashReceiveOperator(ID, meta);
			receive2.setNode(i);
			receives.add(receive2);
			i++;
		}

		for (final Operator receive2 : receives)
		{
			for (final Operator send : sends)
			{
				try
				{
					receive2.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}
		}

		final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(parents.size());
		for (final Operator clone : parents)
		{
			for (final Operator receive2 : receives)
			{
				if (clone.getNode() == receive2.getNode())
				{
					try
					{
						clone.add(receive2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
				}
			}

			final NetworkSendOperator send = new NetworkSendOperator(clone.getNode(), meta);
			sends2.add(send);
			try
			{
				send.add(clone);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}

		final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
		r.setNode(grandParent.getNode());
		for (final NetworkSendOperator send : sends2)
		{
			try
			{
				r.add(send);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}
		}
		try
		{
			grandParent.add(r);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
		makeHierarchical2(r);
		makeHierarchical(r);
		cCache.clear();
		return false;
	}

	private boolean redistributeNL(NetworkReceiveOperator receive) throws Exception
	{
		if (((NestedLoopJoinOperator)receive.parent()).usesHash())
		{
			return redistributeHash(receive);
		}

		return redistributeProduct(receive);
	}

	private boolean redistributeProduct(NetworkReceiveOperator receive) throws Exception
	{
		final Operator parent = receive.parent();
		Operator left = parent.children().get(0);
		Operator right = parent.children().get(1);

		if (isAllAny(left, new HashSet<Operator>()) && left.children().size() == 1)
		{
			verify2ReceivesForProduct(parent);
			left = parent.children().get(0);
			right = parent.children().get(1);
			final Operator grandParent = parent.parent();
			parent.removeChild(left);
			grandParent.removeChild(parent);
			final ArrayList<Operator> grandChildren = new ArrayList<Operator>();
			for (final Operator child : (ArrayList<Operator>)right.children().clone())
			{
				final Operator grandChild = child.children().get(0);
				if (!(grandChild instanceof NetworkReceiveOperator))
				{
					grandChildren.add(grandChild);
				}
				else
				{
					grandChildren.addAll(getGrandChildren(grandChild));
				}
			}

			for (Operator o : grandChildren)
			{
				if (o.parent() != null)
				{
					o.parent().removeChild(o);
				}
			}

			final ArrayList<NetworkSendOperator> sends2 = new ArrayList<NetworkSendOperator>(grandChildren.size());
			for (final Operator grandChild : grandChildren)
			{
				final Operator clone = cloneTree(parent, 0);
				clone.setNode(grandChild.getNode());
				final Operator leftClone = fullyCloneTree(left.children().get(0).children().get(0));
				setNodeForTree(leftClone, grandChild.getNode(), new HashSet<Operator>());
				try
				{
					clone.add(leftClone);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}

				for (final Operator o : (ArrayList<Operator>)clone.children().clone())
				{
					if (!o.equals(leftClone))
					{
						clone.removeChild(o);
					}
				}

				try
				{
					clone.add(grandChild);
					if (grandChild instanceof TableScanOperator)
					{
						final CNFFilter cnf = ((TableScanOperator)grandChild).getFirstCNF();
						if (cnf != null)
						{
							((TableScanOperator)grandChild).setCNFForParent(clone, cnf);
						}
					}
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}

				final NetworkSendOperator send2 = new NetworkSendOperator(clone.getNode(), meta);
				try
				{
					send2.add(clone);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}

				sends2.add(send2);
			}

			final NetworkReceiveOperator r = new NetworkReceiveOperator(meta);
			r.setNode(grandParent.getNode());

			for (final NetworkSendOperator send : sends2)
			{
				try
				{
					r.add(send);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
			}

			try
			{
				grandParent.add(r);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				throw e;
			}

			makeHierarchical(r);
			cCache.clear();
			return false;
		}

		return false;
	}

	private boolean redistributeSemi(NetworkReceiveOperator receive) throws Exception
	{
		if (((SemiJoinOperator)receive.parent()).usesHash())
		{
			return doHashSemi(receive);
		}

		return doNonHashSemi(receive);
	}

	private void redistributeSorts() throws Exception
	{
		final SortOperator sort = getLocalSort(root);
		if (sort != null)
		{
			long card = card(sort);
			if (card > MAX_LOCAL_SORT)
			{
				doSortRedistribution(sort, card);
			}
		}
	}

	private void removeDuplicateReorders(Operator op, HashSet<Operator> touched) throws Exception
	{
		if (touched.contains(op))
		{
			return;
		}

		touched.add(op);
		if (!(op instanceof ReorderOperator))
		{
			for (Operator o : op.children())
			{
				removeDuplicateReorders(o, touched);
			}
		}
		else
		{
			if (op.children().get(0) instanceof ReorderOperator)
			{
				// only need last one
				Operator child = op.children().get(0);
				Operator grandChild = child.children().get(0);
				CNFFilter cnf = null;
				if (grandChild instanceof TableScanOperator)
				{
					cnf = ((TableScanOperator)grandChild).getCNFForParent(child);
				}

				child.removeChild(grandChild);
				op.removeChild(child);
				op.add(grandChild);
				if (cnf != null)
				{
					((TableScanOperator)grandChild).setCNFForParent(op, cnf);
				}

				touched.remove(op);
				removeDuplicateReorders(op, touched);
			}
			else
			{
				for (Operator o : op.children())
				{
					removeDuplicateReorders(o, touched);
				}
			}
		}
	}

	private void removeLocalSendReceive(Operator op, HashSet<Operator> visited) throws Exception
	{
		if (visited.contains(op))
		{
			return;
		}

		visited.add(op);
		if (op instanceof NetworkReceiveOperator && op.getClass().equals(NetworkReceiveOperator.class))
		{
			if (op.children().size() == 1)
			{
				final Operator send = op.children().get(0);
				if (send.getNode() == op.getNode())
				{
					final Operator parent = op.parent();
					parent.removeChild(op);
					final Operator child = send.children().get(0);
					send.removeChild(child);
					try
					{
						parent.add(child);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("", e);
						throw e;
					}
					removeLocalSendReceive(child, visited);
				}
			}
			else
			{
				for (final Operator send : op.children())
				{
					if (send.getNode() != op.getNode())
					{
						for (final Operator s : op.children())
						{
							removeLocalSendReceive(s, visited);
						}

						return;
					}
				}

				final Operator parent = op.parent();
				parent.removeChild(op);
				final Operator union = new UnionOperator(false, meta);
				try
				{
					for (final Operator send : op.children())
					{
						final Operator child = send.children().get(0);
						send.removeChild(child);
						union.add(child);
						union.setNode(child.getNode());
					}
					parent.add(union);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
				removeLocalSendReceive(union, visited);
			}
		}
		else
		{
			for (final Operator o : (ArrayList<Operator>)op.children().clone())
			{
				removeLocalSendReceive(o, visited);
			}
		}
	}

	private void removeUnneededHash() throws Exception
	{
		ArrayList<TableScanOperator> tables = getTables(root, new HashSet<Operator>());
		HashSet<TableScanOperator> temp = new HashSet<TableScanOperator>(tables);
		for (TableScanOperator table : temp)
		{
			if (table.noNodeGroupSet() && table.allNodes() && table.nodeIsHash())
			{
				ArrayList<String> current = (ArrayList<String>)table.getNodeHash().clone();
				// fix current
				int i = 0;
				for (String col : current)
				{
					if (table.getAlias() != null && !table.getAlias().equals(""))
					{
						if (!col.contains("."))
						{
							current.remove(i);
							current.add(i, table.getAlias() + "." + col);
						}
					}
					else
					{
						if (!col.contains("."))
						{
							current.remove(i);
							current.add(i, table.getTable() + "." + col);
						}
					}
				}
				// HRDBMSWorker.logger.debug("Looking at " + table);
				// HRDBMSWorker.logger.debug("Original hash is " + current);
				Operator up = table.firstParent();
				doneWithTable: while (true)
				{
					while (!(up instanceof NetworkSendOperator))
					{
						if (up instanceof RenameOperator)
						{
							// change names in current if need be
							HashMap<String, String> old2New = ((RenameOperator)up).getRenameMap();
							for (String col : (ArrayList<String>)current.clone())
							{
								String newName = old2New.get(col);
								if (newName != null)
								{
									int index = current.indexOf(col);
									current.remove(index);
									current.add(index, newName);
								}
							}
							// HRDBMSWorker.logger.debug("Current has changed to "
							// + current);
						}

						if (up instanceof MultiOperator)
						{
							// take things out of current except for ones that
							// are also in the group by
							current.retainAll(((MultiOperator)up).getKeys());
							// HRDBMSWorker.logger.debug("Current has changed to "
							// + current);
						}

						if (up instanceof RootOperator)
						{
							break doneWithTable;
						}

						up = up.parent();
					}

					if (up instanceof NetworkHashAndSendOperator)
					{
						if (((NetworkHashAndSendOperator)up).parents().size() == MetaData.numWorkerNodes)
						{
							if (((NetworkHashAndSendOperator)up).getHashCols().equals(current) && current.size() > 0)
							{
								// HRDBMSWorker.logger.debug("Removing " + up);
								// remove sends and receives
								Operator grandParent = null;
								for (Operator parent : (ArrayList<Operator>)((NetworkHashAndSendOperator)up).parents().clone())
								{
									if (parent.getNode() == table.getNode())
									{
										grandParent = parent.parent();
										grandParent.removeChild(parent);
										CNFFilter cnf = null;
										parent.removeChild(up);

										Operator child = up.children().get(0);
										if (child instanceof TableScanOperator)
										{
											cnf = ((TableScanOperator)child).getCNFForParent(up);
										}
										up.removeChild(child);
										grandParent.add(child);

										if (cnf != null)
										{
											((TableScanOperator)child).setCNFForParent(grandParent, cnf);
										}
									}
								}

								// set up to grandparent that is on my node
								up = grandParent;
							}
							else
							{
								// HRDBMSWorker.logger.debug("Hashes don't match: "
								// + up);
								current = (ArrayList<String>)((NetworkHashAndSendOperator)up).getHashCols().clone();
								// HRDBMSWorker.logger.debug("Current has changed to "
								// + current);
								// set up to the parent that is on my node
								for (Operator parent : ((NetworkHashAndSendOperator)up).parents())
								{
									if (parent.getNode() == table.getNode())
									{
										up = parent;
									}
								}
							}
						}
						else
						{
							// HRDBMSWorker.logger.debug("Hash and send does not use all nodes. Size = "
							// +
							// ((NetworkHashAndSendOperator)up).parents().size());
							// HRDBMSWorker.logger.debug(up);
							break;
						}
					}
					else
					{
						// HRDBMSWorker.logger.debug("Not a hash and send: " +
						// up);
						break;
					}
				}
			}
		}
	}

	/*
	 * private void sanityCheck(Operator op, int node) throws Exception { if (op
	 * instanceof NetworkSendOperator) { node = op.getNode(); for (Operator o :
	 * op.children()) { sanityCheck(o, node); } } else { if (op.getNode() !=
	 * node) { HRDBMSWorker.logger.debug("P4 sanity check failed");
	 * HRDBMSWorker.logger.debug("Parent is " + op.parent() + " (" +
	 * op.parent().getNode() + ")");
	 * HRDBMSWorker.logger.debug("Children are..."); for (Operator o :
	 * op.parent().children()) { if (o == op) {
	 * HRDBMSWorker.logger.debug("***** " + o + " (" + o.getNode() + ") *****");
	 * } else { HRDBMSWorker.logger.debug(o + " (" + o.getNode() + ")"); } }
	 * throw new Exception("P4 sanity check failed"); }
	 *
	 * for (Operator o : op.children()) { sanityCheck(o, node); } } }
	 */

	private void setNodeForTree(Operator op, int node, HashSet<Operator> touched)
	{
		if (touched.contains(op))
		{
			return;
		}

		touched.add(op);
		op.setNode(node);
		for (final Operator o : op.children())
		{
			setNodeForTree(o, node, touched);
		}
	}

	private boolean treeContains(Operator root, Operator op)
	{
		if (root.equals(op))
		{
			return true;
		}

		for (final Operator o : root.children())
		{
			if (treeContains(o, op))
			{
				return true;
			}
		}

		return false;
	}

	private void verify2ReceivesForHash(Operator op) throws Exception
	{
		try
		{
			if (!(op.children().get(0) instanceof NetworkReceiveOperator))
			{
				final Operator child = op.children().get(0);
				op.removeChild(child);
				final NetworkSendOperator send = new NetworkSendOperator(op.getNode(), meta);
				send.add(child);
				final NetworkReceiveOperator receive = new NetworkReceiveOperator(meta);
				receive.setNode(op.getNode());
				receive.add(send);
				op.add(receive);
				cCache.clear();
			}

			if (!(op.children().get(1) instanceof NetworkReceiveOperator))
			{
				final Operator child = op.children().get(1);
				op.removeChild(child);
				final NetworkSendOperator send = new NetworkSendOperator(op.getNode(), meta);
				send.add(child);
				final NetworkReceiveOperator receive = new NetworkReceiveOperator(meta);
				receive.setNode(op.getNode());
				receive.add(send);
				op.add(receive);
				cCache.clear();
			}
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
	}

	private void verify2ReceivesForProduct(Operator op) throws Exception
	{
		try
		{
			if (!(op.children().get(0) instanceof NetworkReceiveOperator))
			{
				final Operator child = op.children().get(0);
				op.removeChild(child);
				final NetworkSendOperator send = new NetworkSendOperator(-1, meta);
				send.add(child);
				final NetworkReceiveOperator receive = new NetworkReceiveOperator(meta);
				receive.setNode(op.getNode());
				receive.add(send);
				op.add(receive);
				cCache.clear();
			}

			if (!(op.children().get(1) instanceof NetworkReceiveOperator))
			{
				final Operator child = op.children().get(1);
				op.removeChild(child);
				final int ID = id.getAndIncrement();
				final NetworkSendRROperator send = new NetworkSendRROperator(ID, meta);
				send.setNode(child.getNode());
				send.add(child);

				long numNodes = card(child) / MAX_LOCAL_SORT + 1;
				final int starting = getStartingNode(numNodes);
				final ArrayList<NetworkSendOperator> sends = new ArrayList<NetworkSendOperator>();
				int i = 0;
				while (i < numNodes && starting + i < MetaData.numWorkerNodes)
				{
					final NetworkHashReceiveOperator receive = new NetworkHashReceiveOperator(ID, meta);
					receive.setNode(starting + i);
					receive.add(send);
					final NetworkSendOperator send2 = new NetworkSendOperator(starting + i, meta);
					final ReorderOperator reorder = new ReorderOperator(new ArrayList<String>(receive.getPos2Col().values()), meta);
					reorder.setNode(starting + i);
					reorder.add(receive);
					send2.add(reorder);
					sends.add(send2);
					i++;
				}

				final NetworkReceiveOperator receive = new NetworkReceiveOperator(meta);
				receive.setNode(op.getNode());

				for (final NetworkSendOperator send2 : sends)
				{
					receive.add(send2);
				}
				op.add(receive);
				cCache.clear();
			}
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
	}

	private void verify2ReceivesForSemi(Operator op) throws Exception
	{
		try
		{
			if (!(op.children().get(1) instanceof NetworkReceiveOperator))
			{
				final Operator child = op.children().get(1);
				op.removeChild(child);
				final NetworkSendOperator send = new NetworkSendOperator(-1, meta);
				send.add(child);
				final NetworkReceiveOperator receive = new NetworkReceiveOperator(meta);
				receive.setNode(op.getNode());
				receive.add(send);
				op.add(receive);
				cCache.clear();
			}

			if (!(op.children().get(0) instanceof NetworkReceiveOperator))
			{
				final Operator child = op.children().get(0);
				op.removeChild(child);
				final int ID = id.getAndIncrement();
				final NetworkSendRROperator send = new NetworkSendRROperator(ID, meta);
				send.setNode(child.getNode());
				send.add(child);

				final long numNodes = card(child) / MAX_LOCAL_SORT + 1;
				final int starting = getStartingNode(numNodes);
				final ArrayList<NetworkSendOperator> sends = new ArrayList<NetworkSendOperator>();
				int i = 0;
				while (i < numNodes && starting + i < MetaData.numWorkerNodes)
				{
					final NetworkHashReceiveOperator receive = new NetworkHashReceiveOperator(ID, meta);
					receive.setNode(starting + i);
					receive.add(send);
					final NetworkSendOperator send2 = new NetworkSendOperator(starting + i, meta);
					final ReorderOperator reorder = new ReorderOperator(new ArrayList<String>(receive.getPos2Col().values()), meta);
					reorder.setNode(starting + i);
					reorder.add(receive);
					send2.add(reorder);
					sends.add(send2);
					i++;
				}

				final NetworkReceiveOperator receive = new NetworkReceiveOperator(meta);
				receive.setNode(op.getNode());

				for (final NetworkSendOperator send2 : sends)
				{
					receive.add(send2);
				}
				op.add(receive);
				cCache.clear();
			}
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			throw e;
		}
	}
}