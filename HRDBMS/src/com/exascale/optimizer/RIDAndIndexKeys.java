package com.exascale.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import com.exascale.filesystem.RID;

public class RIDAndIndexKeys implements Serializable
{
	private final RID rid;
	private final ArrayList<ArrayList<Object>> indexKeys;

	public RIDAndIndexKeys(RID rid, ArrayList<ArrayList<Object>> indexKeys)
	{
		this.rid = rid;
		this.indexKeys = indexKeys;
	}

	@Override
	public boolean equals(Object rhs)
	{
		if (rhs == null)
		{
			return false;
		}

		if (!(rhs instanceof RIDAndIndexKeys))
		{
			return false;
		}

		RIDAndIndexKeys r = (RIDAndIndexKeys)rhs;
		return rid.equals(r.rid) && indexKeys.equals(r.indexKeys);
	}

	public ArrayList<ArrayList<Object>> getIndexKeys()
	{
		return indexKeys;
	}

	public RID getRID()
	{
		return rid;
	}

	@Override
	public int hashCode()
	{
		int hash = 17;
		hash = hash * 23 + rid.hashCode();
		hash = hash * 23 + indexKeys.hashCode();
		return hash;
	}
}
