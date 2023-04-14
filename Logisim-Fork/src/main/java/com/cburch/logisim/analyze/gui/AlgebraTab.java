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
import com.cburch.logisim.analyze.model.OutputExpressionsEvent;
import com.cburch.logisim.analyze.model.OutputExpressionsListener;
import com.cburch.logisim.analyze.model.Parser;
import com.cburch.logisim.analyze.model.ParserException;
import com.cburch.logisim.util.StringGetter;

class AlgebraTab extends AnalyzerTab implements TabInterface {
	
	private static final long serialVersionUID = 5039742927554394341L;
	
	private ExpressionView prettyView = new ExpressionView();
	private JButton enter = new JButton();
	private JLabel error = new JLabel();
	private ArrayList<JPanel> textFields = new ArrayList<>();
	private int fieldCount = 0;
	
	private MyListener myListener = new MyListener();
	private AnalyzerModel model;
	private AnalyzerModel auxModel; 
	private int curExprStringLength = 0;
	private StringGetter errorMessage;
	JPanel buttons;
	JPanel fieldsPanel;
	GridBagLayout gb;
	GridBagConstraints gc;
	
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
			
			enter.setEnabled(true);
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
			if ((src == enter) && enter.isEnabled()) {
				String text = getField().getText();
				// INÍCIO: Trecho para pegar os inputs
				ArrayList<String> inputs = new ArrayList<>();
				ArrayList<String> outputs = new ArrayList<>();
				outputs.add("y");
				char aux;
				for (int i = 0; i < text.length(); i++) {
					aux = text.charAt(i);
					if (Character.isLetter(aux) && !inputs.contains(Character.toString(aux))) {
						inputs.add(Character.toString(aux));
					}
						
				}
				System.out.println("Inputs: "+inputs);
				inputs.sort(String.CASE_INSENSITIVE_ORDER);
				// FIM: Trecho para pegar os inputs
				if (fieldCount == 0) {
					model.setVariables(inputs, outputs);
				} else {
					auxModel.setVariables(inputs, outputs);
				}
				
				
				
				
				
				
				
				
				Expression expr; 
				try {
					expr = Parser.parse(getField().getText(), fieldCount == 0 ? model : auxModel);
					(fieldCount == 0 ? model : auxModel).getOutputExpressions().setExpression("y", expr, getField().getText());
				} catch (ParserException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("Models equals: "+Boolean.toString(model.getTruthTable().equals(auxModel.getTruthTable())));
				
//				try {
//					System.out.println("MODELO:"+ Parser.parse("a+b",model).toString());
//					System.out.println("MODELO:"+ model.getOutputExpressions().getExpressionString("y"));
//				} catch (ParserException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				//System.out.println("MODELO:"+model.getOutputExpressions().getExpression("y"));
				insertTextField();
				insertUpdate(null);
			}
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
				getField().setText(getCurrentString());
			} else {
				insertUpdate(null);
			}
		}
		
		@Override
		public void expressionChanged(OutputExpressionsEvent event) {
			System.out.println(event.getType());
			
			if (event.getType() == OutputExpressionsEvent.OUTPUT_EXPRESSION) {
				String output = event.getVariable();
				System.out.println(output);
				if (output.equals(getCurrentVariable())) {
					prettyView.setExpression(model.getOutputExpressions().getExpression(output));
					currentStringChanged();
				}
			}
		}
	}

	public AlgebraTab(AnalyzerModel model) {
		this.model = model;
		this.auxModel = new AnalyzerModel();
		model.getOutputExpressions().addOutputExpressionsListener(myListener);
		enter.addActionListener(myListener);
		JTextField field = new JTextField(10);
		JLabel label = new JLabel("test");
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		
		field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), myListener);
		field.getDocument().addDocumentListener(myListener);
		field.setFont(new Font("sans serif", Font.PLAIN, 17));
		panel.add(field);
		panel.add(label);		
		textFields.add(panel);
		
		fieldsPanel = new JPanel( new GridLayout(0, 1) );
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

		
		gb.setConstraints(prettyView, gc);
		add(prettyView);
		prettyView.setVisible(false);
		gc.insets = new Insets(10, 10, 0, 10);
		
		gb.setConstraints(fieldsPanel, gc);
		add(fieldsPanel);
		gb.setConstraints(buttons, gc);
		add(buttons);
		gb.setConstraints(error, gc);
		add(error);
		
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
	
	public void insertTextField() {
		JTextField newField = new JTextField(10);
		JLabel newLabel = new JLabel("correto");
		JPanel newPanel = new JPanel();
		
		
		
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
	
	String getCurrentVariable() {
		return null;
	}
	
	@Override
	void localeChanged() {
		prettyView.localeChanged();
		enter.setText(Strings.get("exprEnterButton"));
		if (errorMessage != null) {
			error.setText(errorMessage.get());
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
		if (msg == null) {
			errorMessage = null;
			error.setText(" ");
		} else {
			errorMessage = msg;
			error.setText(msg.get());
		}
	}
	
	@Override
	void updateTab() {
		String output = getCurrentVariable();
		prettyView.setExpression(model.getOutputExpressions().getExpression(output));
		myListener.currentStringChanged();
	}
}
