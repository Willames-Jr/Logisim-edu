/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.analyze.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TruthTable {
	private class MyListener implements VariableListListener {
		private void inputsChanged(VariableListEvent event) {
			int action = event.getType();
			if (action == VariableListEvent.ADD) {
				for (Map.Entry<String, Entry[]> curEntry : outputColumns.entrySet()) {
					String output = curEntry.getKey();
					Entry[] column = curEntry.getValue();
					Entry[] newColumn = new Entry[2 * column.length];
					for (int i = 0; i < column.length; i++) {
						newColumn[2 * i] = column[i];
						newColumn[2 * i + 1] = column[i];
					}
					outputColumns.put(output, newColumn);
				}
			} else if (action == VariableListEvent.REMOVE) {
				int index = ((Integer) event.getData()).intValue();
				for (Map.Entry<String, Entry[]> curEntry : outputColumns.entrySet()) {
					String output = curEntry.getKey();
					Entry[] column = curEntry.getValue();
					Entry[] newColumn = removeInput(column, index);
					outputColumns.put(output, newColumn);
				}
			} else if (action == VariableListEvent.MOVE) {
				int delta = ((Integer) event.getData()).intValue();
				int newIndex = model.getInputs().indexOf(event.getVariable());
				for (Map.Entry<String, Entry[]> curEntry : outputColumns.entrySet()) {
					String output = curEntry.getKey();
					Entry[] column = curEntry.getValue();
					Entry[] newColumn = moveInput(column, newIndex - delta, newIndex);
					outputColumns.put(output, newColumn);
				}
			}
		}

		@Override
		public void listChanged(VariableListEvent event) {
			if (event.getSource() == model.getInputs()) {
				inputsChanged(event);
			} else {
				outputsChanged(event);
			}
			fireStructureChanged(event);
		}

		private Entry[] moveInput(Entry[] old, int oldIndex, int newIndex) {
			int inputs = model.getInputs().size();
			oldIndex = inputs - 1 - oldIndex;
			newIndex = inputs - 1 - newIndex;
			Entry[] ret = new Entry[old.length];
			int sameMask = (old.length - 1) ^ ((1 << (1 + Math.max(oldIndex, newIndex))) - 1)
					^ ((1 << Math.min(oldIndex, newIndex)) - 1); // bits that
																	// don't
																	// change
			int moveMask = 1 << oldIndex; // bit that moves
			int moveDist = Math.abs(newIndex - oldIndex);
			boolean moveLeft = newIndex > oldIndex;
			int blockMask = (old.length - 1) ^ sameMask ^ moveMask; // bits that
																	// move by
																	// one
			for (int i = 0; i < old.length; i++) {
				int j; // new index
				if (moveLeft) {
					j = (i & sameMask) | ((i & moveMask) << moveDist) | ((i & blockMask) >> 1);
				} else {
					j = (i & sameMask) | ((i & moveMask) >> moveDist) | ((i & blockMask) << 1);
				}
				ret[j] = old[i];
			}
			return ret;
		}

		private void outputsChanged(VariableListEvent event) {
			int action = event.getType();
			if (action == VariableListEvent.ALL_REPLACED) {
				outputColumns.clear();
			} else if (action == VariableListEvent.REMOVE) {
				outputColumns.remove(event.getVariable());
			} else if (action == VariableListEvent.REPLACE) {
				Entry[] column = outputColumns.remove(event.getVariable());
				if (column != null) {
					int index = ((Integer) event.getData()).intValue();
					String newVariable = model.getOutputs().get(index);
					outputColumns.put(newVariable, column);
				}
			}
		}

		private Entry[] removeInput(Entry[] old, int index) {
			int oldInputCount = model.getInputs().size() + 1;
			Entry[] ret = new Entry[old.length / 2];
			int j = 0;
			int mask = 1 << (oldInputCount - 1 - index);
			for (int i = 0; i < old.length; i++) {
				if ((i & mask) == 0) {
					Entry e0 = old[i];
					Entry e1 = old[i | mask];
					ret[j] = (e0 == e1 ? e0 : Entry.DONT_CARE);
					j++;
				}
			}
			return ret;
		}
	}

	private static final Entry DEFAULT_ENTRY = Entry.DONT_CARE;

	public static boolean isInputSet(int row, int column, int inputs) {
		return ((row >> (inputs - 1 - column)) & 0x1) == 1;
	}

	private MyListener myListener = new MyListener();
	private List<TruthTableListener> listeners = new ArrayList<TruthTableListener>();
	private AnalyzerModel model;

	private HashMap<String, Entry[]> outputColumns = new HashMap<String, Entry[]>();

	public TruthTable(AnalyzerModel model) {
		this.model = model;
		model.getInputs().addVariableListListener(myListener);
		model.getOutputs().addVariableListListener(myListener);
	}

	public void addTruthTableListener(TruthTableListener l) {
		listeners.add(l);
	}

	private void fireCellsChanged(int column) {
		TruthTableEvent event = new TruthTableEvent(this, column);
		for (TruthTableListener l : listeners) {
			l.cellsChanged(event);
		}
	}

	private void fireStructureChanged(VariableListEvent cause) {
		TruthTableEvent event = new TruthTableEvent(this, cause);
		for (TruthTableListener l : listeners) {
			l.structureChanged(event);
		}
	}

	public int getInputColumnCount() {
		return model.getInputs().size();
	}

	public Entry getInputEntry(int row, int column) {
		int rows = getRowCount();
		int inputs = model.getInputs().size();
		if (row < 0 || row >= rows) {
			throw new IllegalArgumentException("row index: " + row + " size: " + rows);
		}
		if (column < 0 || column >= inputs) {
			throw new IllegalArgumentException("column index: " + column + " size: " + inputs);
		}

		return isInputSet(row, column, inputs) ? Entry.ONE : Entry.ZERO;
	}

	public String getInputHeader(int column) {
		return model.getInputs().get(column);
	}

	public int getInputIndex(String input) {
		return model.getInputs().indexOf(input);
	}

	public Entry[] getOutputColumn(int column) {
		int outputs = model.getOutputs().size();
		if (column < 0 || column >= outputs) {
			throw new IllegalArgumentException("index: " + column + " size: " + outputs);
		}

		String outputName = model.getOutputs().get(column);
		Entry[] columnData = outputColumns.get(outputName);
		if (columnData == null) {
			columnData = new Entry[getRowCount()];
			Arrays.fill(columnData, DEFAULT_ENTRY);
			outputColumns.put(outputName, columnData);
		}
		return columnData;
	}

	public int getOutputColumnCount() {
		return model.getOutputs().size();
	}

	public Entry getOutputEntry(int row, int column) {
		int outputs = model.getOutputs().size();
		if (row < 0 || row >= getRowCount() || column < 0 || column >= outputs) {
			return Entry.DONT_CARE;
		} else {
			String outputName = model.getOutputs().get(column);
			Entry[] columnData = outputColumns.get(outputName);
			if (columnData == null)
				return DEFAULT_ENTRY;
			if (row < 0 || row >= columnData.length)
				return Entry.DONT_CARE;
			return columnData[row];
		}
	}

	public String getOutputHeader(int column) {
		return model.getOutputs().get(column);
	}

	public int getOutputIndex(String output) {
		return model.getOutputs().indexOf(output);
	}

	public int getRowCount() {
		int sz = model.getInputs().size();
		return 1 << sz;
	}

	public void removeTruthTableListener(TruthTableListener l) {
		listeners.remove(l);
	}

	public void setOutputColumn(int column, Entry[] values) {
		if (values != null && values.length != getRowCount()) {
			throw new IllegalArgumentException("argument to setOutputColumn is wrong length");
		}

		int outputs = model.getOutputs().size();
		if (column < 0 || column >= outputs) {
			throw new IllegalArgumentException("index: " + column + " size: " + outputs);
		}

		String outputName = model.getOutputs().get(column);
		Entry[] oldValues = outputColumns.get(outputName);
		if (oldValues == values)
			return;
		else if (values == null)
			outputColumns.remove(outputName);
		else
			outputColumns.put(outputName, values);
		fireCellsChanged(column);
	}
	
	@Override
	public boolean equals(Object obj) {
		
		if (obj == null) {
			return false;
		}

		if (obj.getClass() != this.getClass()) {
			return false;
		}
		
		/* A thuthTable is equals to another if the conditions are satisfied:
			a) The number of inputs are equal
			b) The number of outputs are equal
			c) The values in column of input are equals
			d) The values in column of outputs are equals
		*/
		final TruthTable table = (TruthTable) obj;
		int inputs = table.getInputColumnCount();
		int outputs = table.getOutputColumnCount();
		int rows = table.getRowCount();
		
		if ((inputs != this.getInputColumnCount()) || (outputs != this.getOutputColumnCount())) {
			return false;
		}
		
		for (int i = 0; i < inputs; i++) {
			if (!table.getInputHeader(i).equals(this.getInputHeader(i))) {
				return false;
			}
		}
		
		// In this case we consider that the inputs obey a sequence.
		// This means that in a table with n numbers of inputs, the column in 
		// position 1 will have the same sequence of zeros and ones of a table 
		// with the same number, n, of inputs.
		// So we just need to compare the sequence of outputs.
		
		for (int c = 0; c < outputs; c++) {
			Entry[] tableColumn = table.getOutputColumn(c);
			Entry[] selfColumn = this.getOutputColumn(c);
			for (int r = 0; r < rows; r++) {
				if (tableColumn[r] != selfColumn[r]) {
					return false;
				}
			}
		}
		return true;
	}
	
	public String toExpression(int format, String output) {
		Entry desired = format == AnalyzerModel.FORMAT_SUM_OF_PRODUCTS ? Entry.ONE : Entry.ZERO;
		String returnExpression = "";
		int outputs = this.getOutputColumnCount();
		int inputs = this.getInputColumnCount();
		int entries = this.getRowCount();
		
		for (int i = 0; i < outputs; i++) {
			String outputHeader = this.getOutputHeader(i);
			if (!outputHeader.equals(output)) continue;
			
			Entry[] actualColumn = this.getOutputColumn(i);
			for (int j = 0; j < entries; j++) {
				Entry actualEntry = actualColumn[j];
				if (actualEntry != desired) continue;
				String inputExpression = "";
				if (!returnExpression.isEmpty()) inputExpression += desired == Entry.ONE ? "+ " : " ";
				inputExpression += "(";		
				for (int k = 0; k < inputs; k++) {
					String inputHeader = this.getInputHeader(k);
					Entry input = this.getInputEntry(j, k);
					
					inputExpression += input == desired ? inputHeader : "~"+inputHeader;
					if ((k + 1) != inputs) {
						inputExpression += desired == Entry.ONE ? " " : "+";
					}
				}
				
				inputExpression += ") ";
				
				returnExpression += inputExpression;				
			}
		} 
		
		return returnExpression;
	}
	
	
	public void setOutputEntry(int row, int column, Entry value) {
		int rows = getRowCount();
		int outputs = model.getOutputs().size();
		if (row < 0 || row >= rows) {
			throw new IllegalArgumentException("row index: " + row + " size: " + rows);
		}
		if (column < 0 || column >= outputs) {
			throw new IllegalArgumentException("column index: " + column + " size: " + outputs);
		}

		String outputName = model.getOutputs().get(column);
		Entry[] columnData = outputColumns.get(outputName);

		if (columnData == null) {
			if (value == DEFAULT_ENTRY)
				return;
			columnData = new Entry[getRowCount()];
			outputColumns.put(outputName, columnData);
			Arrays.fill(columnData, DEFAULT_ENTRY);
			columnData[row] = value;
		} else {
			if (columnData[row] == value)
				return;
			columnData[row] = value;
		}

		fireCellsChanged(column);
	}
}
