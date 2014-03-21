package com.exascale.optimizer;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.misc.DateParser;
import com.exascale.misc.FastStringTokenizer;
import com.exascale.misc.MyDate;
import com.exascale.misc.Utils;

public class Filter implements Cloneable, Serializable
{
	private String val1; // string
	private String op;
	private String val2; // string
	private MyDate dVal1;
	private MyDate dVal2;
	private Long lVal1;
	private Long lVal2;
	private Double fVal1;
	private Double fVal2;
	private String colVal1; // column
	private String colVal2; // column
	private volatile int posVal1 = -1;
	private volatile int posVal2 = -1;
	private boolean always = false;
	private boolean alwaysVal = false;
	// private MySimpleDateFormat sdf = new MySimpleDateFormat("yyyy-MM-dd");
	private String orig1;
	private String orig2;

	public Filter(String val1, String op, String val2) throws Exception
	{
		this.val1 = val1;
		this.orig1 = val1;
		this.op = op;
		this.val2 = val2;
		this.orig2 = val2;

		parseLHS();
		parseRHS();
		setAlwaysVars();
	}

	protected Filter()
	{
	}

	public boolean alwaysFalse()
	{
		return (always && (!alwaysVal));
	}

	public boolean alwaysTrue()
	{
		return (always && alwaysVal);
	}

	@Override
	public boolean equals(Object rhs)
	{
		final Filter r = (Filter)rhs;
		if (orig1.equals(r.orig1) && op.equals(r.op) && orig2.equals(r.orig2))
		{
			return true;
		}

		return false;
	}

	public MyDate getLeftDate()
	{
		return dVal1;
	}

	public double getLeftNumber()
	{
		if (fVal1 != null)
		{
			return fVal1;
		}

		return lVal1;
	}

	public String getLeftString()
	{
		return val1;
	}

	public MyDate getRightDate()
	{
		return dVal2;
	}

	public double getRightNumber()
	{
		if (fVal2 != null)
		{
			return fVal2;
		}

		return lVal2;
	}

	public String getRightString()
	{
		return val2;
	}

	@Override
	public int hashCode()
	{
		return orig1.hashCode() + op.hashCode() + orig2.hashCode();
	}

	public String leftColumn()
	{
		return colVal1;
	}

	public boolean leftIsColumn()
	{
		return colVal1 != null;
	}

	public boolean leftIsDate()
	{
		return dVal1 != null;
	}

	public boolean leftIsNumber()
	{
		return fVal1 != null || lVal1 != null;
	}

	public Object leftLiteral()
	{
		if (fVal1 != null)
		{
			return fVal1;
		}

		if (dVal1 != null)
		{
			return dVal1;
		}

		if (lVal1 != null)
		{
			return lVal1;
		}

		return val1;
	}

	public String leftOrig()
	{
		return orig1;
	}

	public String op()
	{
		return op;
	}

	public final boolean passes(ArrayList<Object> lRow, ArrayList<Object> rRow, HashMap<String, Integer> cols2Pos) throws Exception
	{
		if (cols2Pos == null)
		{
			HRDBMSWorker.logger.error("Filter.passes() called with null cols2Pos!");
			System.exit(1);
		}
		if (always)
		{
			return alwaysVal;
		}

		if (colVal1 != null)
		{
			// left side is a column
			Object lo = null;
			if (posVal1 == -1)
			{
				posVal1 = cols2Pos.get(colVal1);
			}

			final int pos1 = posVal1;
			lo = get(lRow, rRow, pos1);

			if (lo instanceof MyDate)
			{
				if (dVal2 != null)
				{
					return compare((MyDate)lo, dVal2);
				}
				else
				{
					if (posVal2 == -1)
					{
						posVal2 = cols2Pos.get(colVal2);
					}

					final int pos2 = posVal2;
					final MyDate ro = (MyDate)get(lRow, rRow, pos2);
					return compare((MyDate)lo, ro);
				}
			}
			else if (lo instanceof String)
			{
				if (colVal2 != null)
				{
					if (posVal2 == -1)
					{
						try
						{
							posVal2 = cols2Pos.get(colVal2);
						}
						catch (final Exception e)
						{
							HRDBMSWorker.logger.error("Error looking up column " + colVal2 + " in " + cols2Pos, e);
							System.exit(1);
						}
					}

					final int pos2 = posVal2;
					String ro = null;
					try
					{
						ro = (String)get(lRow, rRow, pos2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("Error fetching column from row in Filter", e);
						System.exit(1);
					}
					return compare((String)lo, ro);
				}
				else
				{
					return compare((String)lo, val2);
				}
			}
			else if (lo instanceof Double)
			{
				if (fVal2 != null)
				{
					return compare((Double)lo, fVal2);
				}
				else if (lVal2 != null)
				{
					return compare((Double)lo, new Double(lVal2));
				}
				else
				{
					if (posVal2 == -1)
					{
						posVal2 = cols2Pos.get(colVal2);
					}

					final int pos2 = posVal2;
					final Object o = get(lRow, rRow, pos2);
					try
					{
						return compare((Double)lo, new Double(((Number)o).doubleValue()));
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error(this.toString(), e);
						System.exit(1);
					}
				}
			}
			else if (lo instanceof Long)
			{
				if (fVal2 != null)
				{
					return compare(new Double((Long)lo), fVal2);
				}
				else if (lVal2 != null)
				{
					return compare((Long)lo, lVal2);
				}
				else
				{
					if (posVal2 == -1)
					{
						posVal2 = cols2Pos.get(colVal2);
					}

					final int pos2 = posVal2;
					final Object o = get(lRow, rRow, pos2);
					return compare(((Long)lo).doubleValue(), ((Number)o).doubleValue());
				}
			}
			else if (lo instanceof Integer)
			{
				if (fVal2 != null)
				{
					return compare(new Double((Integer)lo), fVal2);
				}
				else if (lVal2 != null)
				{
					return compare(new Long((Integer)lo), lVal2);
				}
				else
				{
					if (posVal2 == -1)
					{
						try
						{
							posVal2 = cols2Pos.get(colVal2);
						}
						catch (final Exception e)
						{
							HRDBMSWorker.logger.error("Error fetching position from colsPos in Filter");
							HRDBMSWorker.logger.error(cols2Pos);
							HRDBMSWorker.logger.error(colVal2);
							System.exit(1);
						}
					}

					final int pos2 = posVal2;
					final Object o = get(lRow, rRow, pos2);
					return compare(((Integer)lo).doubleValue(), ((Number)o).doubleValue());
				}
			}

			throw new Exception("How the hell did I get here!");
		}
		else if (lVal1 != null)
		{
			// left hand side is an int
			// right hand side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final Object o = get(lRow, rRow, pos);
			return compare(lVal1.doubleValue(), ((Number)o).doubleValue());
		}
		else if (dVal1 != null)
		{
			// left hand side is a date
			// right hand side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final MyDate o = (MyDate)get(lRow, rRow, pos);
			return compare(dVal1, o);
		}
		else if (fVal1 != null)
		{
			// the left side is a floating point value
			// the ride side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final Object o = get(lRow, rRow, pos);
			return compare(fVal1, new Double(((Number)o).doubleValue()));
		}
		else
		{
			// left hand side is string literal in val1
			// right hand side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final String o = (String)get(lRow, rRow, pos);
			return compare(dVal1, o);
		}
	}

	public final boolean passes(ArrayList<Object> row, HashMap<String, Integer> cols2Pos) throws Exception
	{
		if (cols2Pos == null)
		{
			HRDBMSWorker.logger.error("Filter.passes() called with null cols2Pos!");
			System.exit(1);
		}
		if (always)
		{
			return alwaysVal;
		}

		if (colVal1 != null)
		{
			// left side is a column
			if (posVal1 == -1)
			{
				try
				{
					posVal1 = cols2Pos.get(colVal1);
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("Failed to lookup " + colVal1 + " in " + cols2Pos);
					System.exit(1);
				}
			}

			final int pos1 = posVal1;
			final Object lo = row.get(pos1);

			if (lo instanceof MyDate)
			{
				if (dVal2 != null)
				{
					return compare((MyDate)lo, dVal2);
				}
				else
				{
					if (posVal2 == -1)
					{
						posVal2 = cols2Pos.get(colVal2);
					}

					final int pos2 = posVal2;
					final MyDate ro = (MyDate)row.get(pos2);
					return compare((MyDate)lo, ro);
				}
			}
			else if (lo instanceof String)
			{
				if (colVal2 != null)
				{
					if (posVal2 == -1)
					{
						try
						{
							posVal2 = cols2Pos.get(colVal2);
						}
						catch (final Exception e)
						{
							HRDBMSWorker.logger.error("Error looking up column " + colVal2 + " in " + cols2Pos);
							System.exit(1);
						}
					}

					final int pos2 = posVal2;
					String ro = null;
					try
					{
						ro = (String)row.get(pos2);
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error("Error fetching column from row in Filter", e);
						System.exit(1);
					}
					return compare((String)lo, ro);
				}
				else
				{
					return compare((String)lo, val2);
				}
			}
			else if (lo instanceof Double)
			{
				if (fVal2 != null)
				{
					return compare((Double)lo, fVal2);
				}
				else if (lVal2 != null)
				{
					return compare((Double)lo, new Double(lVal2));
				}
				else
				{
					if (posVal2 == -1)
					{
						try
						{
							posVal2 = cols2Pos.get(colVal2);
						}
						catch (final Exception e)
						{
							HRDBMSWorker.logger.error(this.toString(), e);
							HRDBMSWorker.logger.error(colVal2);
							HRDBMSWorker.logger.error(cols2Pos);
							System.exit(1);
						}
					}

					final int pos2 = posVal2;
					final Object o = row.get(pos2);
					try
					{
						return compare((Double)lo, new Double(((Number)o).doubleValue()));
					}
					catch (final Exception e)
					{
						HRDBMSWorker.logger.error(this.toString(), e);
						System.exit(1);
					}
				}
			}
			else if (lo instanceof Long)
			{
				if (fVal2 != null)
				{
					return compare(new Double((Long)lo), fVal2);
				}
				else if (lVal2 != null)
				{
					return compare((Long)lo, lVal2);
				}
				else
				{
					if (posVal2 == -1)
					{
						posVal2 = cols2Pos.get(colVal2);
					}

					final int pos2 = posVal2;
					final Object o = row.get(pos2);
					return compare(((Long)lo).doubleValue(), ((Number)o).doubleValue());
				}
			}
			else if (lo instanceof Integer)
			{
				if (fVal2 != null)
				{
					return compare(new Double((Integer)lo), fVal2);
				}
				else if (lVal2 != null)
				{
					return compare(new Long((Integer)lo), lVal2);
				}
				else
				{
					if (posVal2 == -1)
					{
						try
						{
							posVal2 = cols2Pos.get(colVal2);
						}
						catch (final Exception e)
						{
							HRDBMSWorker.logger.error("Error fetching position from colsPos in Filter", e);
							System.exit(1);
						}
					}

					final int pos2 = posVal2;
					final Object o = row.get(pos2);
					return compare(((Integer)lo).doubleValue(), ((Number)o).doubleValue());
				}
			}

			throw new Exception("How the hell did I get here!");
		}
		else if (lVal1 != null)
		{
			// left hand side is an int
			// right hand side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final Object o = row.get(pos);

			return compare(lVal1.doubleValue(), ((Number)o).doubleValue());
		}
		else if (dVal1 != null)
		{
			// left hand side is a date
			// right hand side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final MyDate o = (MyDate)row.get(pos);
			return compare(dVal1, o);
		}
		else if (fVal1 != null)
		{
			// the left side is a floating point value
			// the ride side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final Object o = row.get(pos);

			return compare(fVal1, new Double(((Number)o).doubleValue()));
		}
		else
		{
			// left hand side is string literal in val1
			// right hand side is a column
			if (posVal2 == -1)
			{
				posVal2 = cols2Pos.get(colVal2);
			}

			final int pos = posVal2;
			final String o = (String)row.get(pos);
			return compare(dVal1, o);
		}
	}

	public String rightColumn()
	{
		return colVal2;
	}

	public boolean rightIsColumn()
	{
		return colVal2 != null;
	}

	public boolean rightIsDate()
	{
		return dVal2 != null;
	}

	public boolean rightIsNumber()
	{
		return fVal2 != null || lVal2 != null;
	}

	public Object rightLiteral()
	{
		if (fVal2 != null)
		{
			return fVal2;
		}

		if (dVal2 != null)
		{
			return dVal2;
		}

		if (lVal2 != null)
		{
			return lVal2;
		}

		return val2;
	}

	public String rightOrig()
	{
		return orig2;
	}

	@Override
	public String toString()
	{
		return orig1 + " " + op + " " + orig2;
	}

	public void updateLeftColumn(String newCol)
	{
		colVal1 = newCol;
		orig1 = newCol;
	}

	public void updateRightColumn(String newCol)
	{
		colVal2 = newCol;
		orig2 = newCol;
	}

	private boolean compare(Comparable lhs, Comparable rhs) throws Exception
	{
		if (op.equals("E"))
		{
			return lhs.equals(rhs);
		}

		if (op.equals("L"))
		{
			return lhs.compareTo(rhs) < 0;
		}

		if (op.equals("LE"))
		{
			return lhs.compareTo(rhs) <= 0;
		}

		if (op.equals("G"))
		{
			return lhs.compareTo(rhs) > 0;
		}

		if (op.equals("GE"))
		{
			return lhs.compareTo(rhs) >= 0;
		}

		if (op.equals("NE"))
		{
			return !lhs.equals(rhs);
		}

		if (op.equals("LI"))
		{
			return ((String)lhs).matches(((String)rhs).replaceAll("%", ".*")); // TODO
																				// replace
																				// special
																				// characters
																				// first
																				// and
																				// check
																				// usage
																				// of
																				// \
		}

		if (op.equals("NL"))
		{
			return (!((String)lhs).matches(((String)rhs).replaceAll("%", ".*"))); // TODO
																					// replace
																					// special
																					// characters
																					// first
																					// and
																					// check
																					// usage
																					// of
																					// \
		}

		throw new Exception("Unknown op type in Filter");
	}

	private boolean compare(double lhs, double rhs) throws Exception
	{
		if (op.equals("E"))
		{
			return lhs == rhs;
		}

		if (op.equals("L"))
		{
			return lhs < rhs;
		}

		if (op.equals("LE"))
		{
			return lhs <= rhs;
		}

		if (op.equals("G"))
		{
			return lhs > rhs;
		}

		if (op.equals("GE"))
		{
			return lhs >= rhs;
		}

		if (op.equals("NE"))
		{
			return lhs != rhs;
		}

		throw new Exception("Unknown op type in Filter");
	}

	private Object get(ArrayList<Object> lRow, ArrayList<Object> rRow, int pos)
	{
		if (pos < lRow.size())
		{
			return lRow.get(pos);
		}
		else
		{
			return rRow.get(pos - lRow.size());
		}
	}

	private void parseLHS() throws ParseException
	{
		if (val1.startsWith("DATE('"))
		{
			String temp = val1.substring(6);
			final FastStringTokenizer tokens = new FastStringTokenizer(temp, "'", false);
			temp = tokens.nextToken();

			dVal1 = DateParser.parse(temp);
		}
		else if (val1.startsWith("'"))
		{
			val1 = val1.substring(1);
			val1 = val1.substring(0, val1.length() - 1);
		}
		else if ((val1.charAt(0) >= '0' && val1.charAt(0) <= '9') || val1.charAt(0) == '-')
		{
			if (val1.contains("."))
			{
				fVal1 = Utils.parseDouble(val1);
			}
			else
			{
				lVal1 = Utils.parseLong(val1);
			}
		}
		else
		{
			colVal1 = val1;
		}
	}

	private void parseRHS() throws ParseException
	{
		if (val2.startsWith("DATE('"))
		{
			String temp = val2.substring(6);
			final FastStringTokenizer tokens = new FastStringTokenizer(temp, "'", false);
			temp = tokens.nextToken();

			dVal2 = DateParser.parse(temp);
		}
		else if (val2.startsWith("'"))
		{
			val2 = val2.substring(1);
			val2 = val2.substring(0, val2.length() - 1);
		}
		else if ((val2.charAt(0) >= '0' && val2.charAt(0) <= '9') || val2.charAt(0) == '-')
		{
			if (val2.contains("."))
			{
				fVal2 = Utils.parseDouble(val2);
			}
			else
			{
				lVal2 = Utils.parseLong(val2);
			}
		}
		else
		{
			colVal2 = val2;
		}
	}

	private void setAlwaysVars() throws Exception
	{
		if (fVal1 != null)
		{
			if (fVal2 != null)
			{
				always = true;
				alwaysVal = compare(fVal1, fVal2);
			}
			else if (lVal2 != null)
			{
				always = true;
				alwaysVal = compare(fVal1, new Double(lVal2));
			}
		}
		else if (lVal1 != null)
		{
			if (lVal2 != null)
			{
				always = true;
				alwaysVal = compare(lVal1, lVal2);
			}
			else if (fVal2 != null)
			{
				always = true;
				alwaysVal = compare(new Double(lVal1), fVal2);
			}
		}
		else if (dVal1 != null)
		{
			if (dVal2 != null)
			{
				always = true;
				alwaysVal = compare(dVal1, dVal2);
			}
		}
		else if (colVal1 == null)
		{
			// string literal
			if (lVal2 == null && dVal2 == null && fVal2 == null && colVal2 == null)
			{
				always = true;
				alwaysVal = compare(val1, val2);
			}
		}
		else
		{
			if (colVal2 != null)
			{
				if (colVal1.equals(colVal2))
				{
					if (op.equals("E"))
					{
						always = true;
						alwaysVal = true;
					}
					else if (op.equals("NE"))
					{
						always = true;
						alwaysVal = false;
					}
				}
			}
		}
	}

	@Override
	protected Filter clone()
	{
		try
		{
			final Filter retval = new Filter();
			retval.val1 = val1; // string
			retval.op = op;
			retval.val2 = val2; // string
			retval.dVal1 = dVal1;
			retval.dVal2 = dVal2;
			retval.lVal1 = lVal1;
			retval.lVal2 = lVal2;
			retval.fVal1 = fVal1;
			retval.fVal2 = fVal2;
			retval.colVal1 = colVal1; // column
			retval.colVal2 = colVal2; // column
			retval.posVal1 = -1;
			retval.posVal2 = -1;
			retval.always = always;
			retval.alwaysVal = alwaysVal;
			retval.orig1 = orig1;
			retval.orig2 = orig2;
			return retval;
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("", e);
			System.exit(1);
		}

		return null;
	}
}