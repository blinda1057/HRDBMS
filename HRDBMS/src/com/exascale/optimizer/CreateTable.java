package com.exascale.optimizer;

import java.util.ArrayList;

public class CreateTable extends SQLStatement
{
	private final TableName table;
	private final ArrayList<ColDef> cols;
	private final PrimaryKey pk;
	private final String nodeGroupExp;
	private final String nodeExp;
	private final String deviceExp;
	private final int type;
	private ArrayList<Integer> colOrder;
	private ArrayList<Integer> organization;

	public CreateTable(TableName table, ArrayList<ColDef> cols, PrimaryKey pk, String nodeGroupExp, String nodeExp, String deviceExp, int type)
	{
		this.table = table;
		this.cols = cols;
		this.pk = pk;
		this.nodeGroupExp = nodeGroupExp;
		this.nodeExp = nodeExp;
		this.deviceExp = deviceExp;
		this.type = type;
	}

	public CreateTable(TableName table, ArrayList<ColDef> cols, PrimaryKey pk, String nodeGroupExp, String nodeExp, String deviceExp, int type, ArrayList<Integer> colOrder)
	{
		this.table = table;
		this.cols = cols;
		this.pk = pk;
		this.nodeGroupExp = nodeGroupExp;
		this.nodeExp = nodeExp;
		this.deviceExp = deviceExp;
		this.type = type;
		this.colOrder = colOrder;
	}

	public ArrayList<Integer> getColOrder()
	{
		return colOrder;
	}

	public ArrayList<ColDef> getCols()
	{
		return cols;
	}

	public String getDeviceExp()
	{
		return deviceExp;
	}

	public String getNodeExp()
	{
		return nodeExp;
	}

	public String getNodeGroupExp()
	{
		return nodeGroupExp;
	}

	public ArrayList<Integer> getOrganization()
	{
		return organization;
	}

	public PrimaryKey getPK()
	{
		return pk;
	}

	public TableName getTable()
	{
		return table;
	}

	public int getType()
	{
		return type;
	}

	public void setOrganization(ArrayList<Integer> organization)
	{
		this.organization = organization;
	}
}
