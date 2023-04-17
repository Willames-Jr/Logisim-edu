package com.cburch.logisim.analyze.gui;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.OutputExpressions;
import com.cburch.logisim.analyze.model.OutputExpressionsEvent;
import com.cburch.logisim.analyze.model.OutputExpressionsListener;
import com.cburch.logisim.analyze.model.Parser;
import com.cburch.logisim.analyze.model.ParserException;
import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.analyze.model.VariableList;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.util.StringGetter;

class AlgebraTab extends AnalyzerTab implements TabInterface {
	
	private static final long serialVersionUID = 5039742927554394341L;
	
	
	private OutputSelector selector;
	private ExpressionView prettyView = new ExpressionView();
	private JButton enter = new JButton();
	private ArrayList<JPanel> textFields = new ArrayList<>();
	private int fieldCount = 0;
	private MyListener myListener = new MyListener();
	private AnalyzerModel model;
	private AnalyzerModel auxModel; 
	private OutputExpressions minimalOutputs;
	private JLabel error;
	private int curExprStringLength = 0;
	private StringGetter errorMessage;
	private boolean isChecking = false; // used to check if need update 
	private JPanel buttons;
	private JPanel tables;
	private JPanel fieldsPanel;
	private GridBagLayout gb;
	private GridBagConstraints gc;
	
	private class MyListener extends AbstractAction
		implements DocumentListener, OutputExpressionsListener, ItemListener {
		
		private static final long serialVersionUID = 5033768701031031315L;
		boolean edited = false;
		
		private String getCurrentString() {
			String output = getCurrentVariable();
			return output == null ? "" : model.getOutputExpressions().getExpressionString(output);
		}
		
		@Override
		public void insertUpdate(DocumentEvent event) {
			String curText = getField().getText();
			
			edited = curText.length() != curExprStringLength || !curText.equals(getCurrentString());
			
			if (auxModel != null && fieldCount == 0) {
				insertTextField();
				return;
			}
			// Only check truth table if enter button is pressed
			if (!isChecking) {
				return;
			}
			if (auxModel == null ? false : !model.getTruthTable().equals(auxModel.getTruthTable())) {
				getLabel().setText("Incorrect");
				removeAllTables();
				addTruthTable(model.getTruthTable(), "Original expression:");
				addTruthTable(auxModel.getTruthTable(), "Your expression:");
				return;
			} else {
				
				if (fieldCount == 0) return;
				
				String exprString = getField().getText();
				Expression expr;
				try {
					expr = Parser.parse(exprString, fieldCount == 0 ? model : auxModel);
					model.getOutputExpressions().setExpression(getCurrentVariable(), expr, exprString);
				} catch (ParserException e) {
					e.printStackTrace();
				}
				
//				if (auxModel.getOutputExpressions().getExpressionString(getCurrentVariable()).equals(minimalOutputs.getExpressionString(getCurrentVariable())) ) {
//					getLabel().setText("Minimal Expresion");
//					getField().setEnabled(false);
//				} else {
//					
//				}
				getLabel().setText("Correct");
				insertTextField();					
				removeAllTables();
				
				return;
			}
		}
		
		@Override
		public void itemStateChanged(ItemEvent event) {
			updateTab();
		}
		
		@Override
		public void removeUpdate(DocumentEvent event) {
			insertUpdate(event);
		}
		
		@Override
		public void actionPerformed(ActionEvent event) {
			Object src = event.getSource();

			if (auxModel == null) {
				minimalOutputs = model.getOutputExpressions();
			}
//			try {
//				
//			} catch (Exception e){
//				return;
//			}
			
			if ((src == enter) && enter.isEnabled()) {
				isChecking = true;
				if (auxModel == null) {
					auxModel = new AnalyzerModel();
					auxModel.setCurrentCircuit(model.getCurrentProject(), model.getCurrentCircuit());
				}
				String text = getField().getText();
//				// START: getting and setting outputs
				ArrayList<String> inputs = new ArrayList<>();
				ArrayList<String> outputs = new ArrayList<>();
				outputs.add(getCurrentVariable());
				char aux;
				for (int i = 0; i < text.length(); i++) {
					aux = text.charAt(i);
					if (Character.isLetter(aux) && !inputs.contains(Character.toString(aux))) {
						inputs.add(Character.toString(aux));
					}
				}
				inputs.sort(String.CASE_INSENSITIVE_ORDER);
				
				auxModel.setVariables(inputs, outputs);
				
				// END: getting and setting outputs 
				try {
					String exprString = getField().getText();
					Expression expr = Parser.parse(exprString, fieldCount == 0 ? model : auxModel);
					(fieldCount == 0 ? model : auxModel).getOutputExpressions().setExpression(getCurrentVariable(), expr, exprString);
					insertUpdate(null);
					setError(null);
				} catch (ParserException ex) {
					setError(ex.getMessageGetter());
				}
			}
			isChecking = false;
			getField().grabFocus();
		}
		
		@Override
		public void changedUpdate(DocumentEvent event) {
			insertUpdate(event);
		}
		
		private void currentStringChanged() {
			String output = getCurrentVariable();
			String exprString = model.getOutputExpressions().getExpressionString(output);
			curExprStringLength = exprString.length();
			if (!edited) {
				setError(null);
				//getField().setText(getCurrentString());
			}
		}
		
		@Override
		public void expressionChanged(OutputExpressionsEvent event) {			
			if (event.getType() == OutputExpressionsEvent.OUTPUT_EXPRESSION) {
				String output = event.getVariable();
				if (output.equals(getCurrentVariable())) {
					prettyView.setExpression(model.getOutputExpressions().getExpression(output));
					if (auxModel == null) {
						getField().setText(model.getTruthTable().toExpression(0, "x"));
					}
					currentStringChanged();
				}
			}
		}
	}

	public AlgebraTab(AnalyzerModel model) {
		this.model = model;
		
		
		model.getOutputExpressions().addOutputExpressionsListener(myListener);
		enter.addActionListener(myListener);
		JTextField field = new JTextField(10);
		JLabel label = new JLabel("First input");
		error = new JLabel("");
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		
		field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), myListener);
		field.getDocument().addDocumentListener(myListener);
		field.setFont(new Font("sans serif", Font.PLAIN, 17));
		panel.add(field);
		panel.add(label);
		
		textFields.add(panel);
		
		
		fieldsPanel = new JPanel( new GridLayout(0, 1) );
		fieldsPanel.setAlignmentX(0);
		fieldsPanel.add(panel);
		
		

		
		buttons = new JPanel();
		buttons.add(enter);
		
		gb = new GridBagLayout();
		gc = new GridBagConstraints();
		setLayout(gb);
		gc.weightx = 0;
		gc.gridx = 0;
		gc.gridy = GridBagConstraints.RELATIVE;
		gc.fill = GridBagConstraints.BOTH;

		selector = new OutputSelector(model);
		JPanel selectorPanel = selector.createPanel();
		
		
		gb.setConstraints(prettyView, gc);
		add(prettyView);
		prettyView.setVisible(false);
		gc.insets = new Insets(10, 10, 0, 10);
		
		gb.setConstraints(selectorPanel, gc);
		add(selectorPanel);
		
		JPanel fieldsTables = new JPanel();
		tables = new JPanel();
		
		fieldsTables.add(fieldsPanel);
		fieldsTables.add(tables);
		
		gb.setConstraints(fieldsPanel, gc);
		add(fieldsPanel);
		gb.setConstraints(tables, gc);
		add(tables);
		
		gb.setConstraints(error, gc);
		add(error);
		gb.setConstraints(buttons, gc);
		add(buttons);
		
//		
		
		
		myListener.insertUpdate(null);
		setError(null);
	}
	
	@Override
	public void copy() {
		getField().requestFocus();
		getField().copy();
	}
	
	@Override
	public void delete() {
		getField().requestFocus();
		getField().replaceSelection("");
	}
	
	JTextField getField() {
		return (JTextField)textFields.get(fieldCount).getComponent(0);
	}
	
	JLabel getLabel() {
		return (JLabel)textFields.get(fieldCount).getComponent(1);
	}
	
	JLabel getError() {
		return error;
	}
	
	public void insertTextField() {
		JTextField newField = new JTextField(10);
		JLabel newLabel = new JLabel("");
		JPanel newPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		
		newField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), myListener);
		newField.getDocument().addDocumentListener(myListener);
		newField.setFont(new Font("sans serif", Font.PLAIN, 17));
		getField().setEnabled(false);
		newPanel.add(newField);
		newPanel.add(newLabel);
		textFields.add(newPanel);
		fieldCount ++;
		fieldsPanel.add(newPanel);
		updateTab();
	}
	
	public void addTruthTable(TruthTable table, String title) {
		JLabel tableTitle = new JLabel(title);
		TableTab newTable = new TableTab(table);
		JPanel panel = new JPanel();
		newTable.localeChanged();
		panel.add(tableTitle);
		panel.add(newTable);
		
		tables.add(panel);
		updateTab();
	}
	
	public void removeAllTables() {
		tables.removeAll();
		updateTab();
	}
	
	String getCurrentVariable() {
		return selector.getSelectedOutput();
	}
	
	@Override
	void localeChanged() {
		selector.localeChanged();
		prettyView.localeChanged();
		enter.setText(Strings.get("exprEnterButton"));
		if (errorMessage != null) {
			getError().setText(errorMessage.get());
		}
	}
	
	@Override 
	public void paste() {
		getField().requestFocus();
		getField().paste();
	}
	
	void registerDefaultButtons(DefaultRegistry registry) {
		registry.registerDefaultButton(getField(), enter);
	}
	
	@Override
	public void selectAll() {
		getField().requestFocus();
		getField().selectAll();
	}
	
	private void setError(StringGetter msg) {
		JLabel actualError = getError();
		if (msg == null) {
			errorMessage = null;
			actualError.setText(" ");
		} else {
			errorMessage = msg;
			actualError.setText(msg.get());
		}
	}

	
	@Override
	void updateTab() {
		String output = getCurrentVariable();
		prettyView.setExpression(model.getOutputExpressions().getExpression(output));
		myListener.currentStringChanged();
	}
}
