package com.exascale.optimizer;

import java.util.ArrayList;

public class Insert extends SQLStatement
{
	private final TableName table;
	private ArrayList<Expression> exps;
	private boolean fromSelect = false;
	private FullSelect select;

	public Insert(TableName table, ArrayList<Expression> exps)
	{
		this.table = table;
		this.exps = exps;
	}

	public Insert(TableName table, FullSelect select)
	{
		this.table = table;
		this.select = select;
		fromSelect = true;
	}

	public boolean fromSelect()
	{
		return fromSelect;
	}

	public ArrayList<Expression> getExpressions()
	{
		return exps;
	}

	public FullSelect getSelect()
	{
		return select;
	}

	public TableName getTable()
	{
		return table;
	}
}
