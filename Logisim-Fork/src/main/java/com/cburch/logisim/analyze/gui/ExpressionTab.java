/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.analyze.gui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.OutputExpressionsEvent;
import com.cburch.logisim.analyze.model.OutputExpressionsListener;
import com.cburch.logisim.analyze.model.Parser;
import com.cburch.logisim.analyze.model.ParserException;
import com.cburch.logisim.util.StringGetter;

class ExpressionTab extends AnalyzerTab implements TabInterface {
	private class MyListener extends AbstractAction
			implements DocumentListener, OutputExpressionsListener, ItemListener {
		/**
		 * 
		 */
		private static final long serialVersionUID = 5687049125095561035L;
		boolean edited = false;

		@Override
		public void actionPerformed(ActionEvent event) {
			Object src = event.getSource();
			if (src == clear) {
				setError(null);
				field.setText("");
				field.grabFocus();
			} else if (src == revert) {
				setError(null);
				field.setText(getCurrentString());
				field.grabFocus();
			} else if ((src == field || src == enter) && enter.isEnabled()) {
				try {
					String exprString = field.getText();
					Expression expr = Parser.parse(field.getText(), model);
					setError(null);
					model.getOutputExpressions().setExpression(getCurrentVariable(), expr, exprString);
					insertUpdate(null);
				} catch (ParserException ex) {
					setError(ex.getMessageGetter());
					field.setCaretPosition(ex.getOffset());
					field.moveCaretPosition(ex.getEndOffset());
				}
				field.grabFocus();
			}
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
				field.setText(getCurrentString());
			} else {
				insertUpdate(null);
			}
		}

		@Override
		public void expressionChanged(OutputExpressionsEvent event) {
			
			if (event.getType() == OutputExpressionsEvent.OUTPUT_EXPRESSION) {
				String output = event.getVariable();
				if (output.equals(getCurrentVariable())) {
					prettyView.setExpression(model.getOutputExpressions().getExpression(output));
					currentStringChanged();
				}
			}
		}

		private String getCurrentString() {
			String output = getCurrentVariable();
			return output == null ? "" : model.getOutputExpressions().getExpressionString(output);
		}

		@Override
		public void insertUpdate(DocumentEvent event) {
			String curText = field.getText();
			edited = curText.length() != curExprStringLength || !curText.equals(getCurrentString());

			boolean enable = (edited && getCurrentVariable() != null);
			clear.setEnabled(curText.length() > 0);
			revert.setEnabled(enable);
			enter.setEnabled(enable);
		}

		@Override
		public void itemStateChanged(ItemEvent event) {
			updateTab();
		}

		@Override
		public void removeUpdate(DocumentEvent event) {
			insertUpdate(event);
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 4349747751518665612L;

	private OutputSelector selector;
	private ExpressionView prettyView = new ExpressionView();
	private JTextArea field = new JTextArea(4, 25);
	private JButton clear = new JButton();
	private JButton revert = new JButton();
	private JButton enter = new JButton();
	private JLabel error = new JLabel();

	private MyListener myListener = new MyListener();
	private AnalyzerModel model;
	private int curExprStringLength = 0;
	private StringGetter errorMessage;

	public ExpressionTab(AnalyzerModel model) {
		this.model = model;
		selector = new OutputSelector(model);

		model.getOutputExpressions().addOutputExpressionsListener(myListener);
		selector.addItemListener(myListener);
		clear.addActionListener(myListener);
		revert.addActionListener(myListener);
		enter.addActionListener(myListener);
		field.setLineWrap(true);
		field.setWrapStyleWord(true);
		field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), myListener);
		field.getDocument().addDocumentListener(myListener);
		field.setFont(new Font("sans serif", Font.PLAIN, 14));

		JPanel buttons = new JPanel();
		buttons.add(clear);
		buttons.add(revert);
		buttons.add(enter);

		GridBagLayout gb = new GridBagLayout();
		GridBagConstraints gc = new GridBagConstraints();
		setLayout(gb);
		gc.weightx = 1.0;
		gc.gridx = 0;
		gc.gridy = GridBagConstraints.RELATIVE;
		gc.fill = GridBagConstraints.BOTH;

		JPanel selectorPanel = selector.createPanel();
		gb.setConstraints(selectorPanel, gc);
		add(selectorPanel);
		gb.setConstraints(prettyView, gc);
		add(prettyView);
		gc.insets = new Insets(10, 10, 0, 10);
		JScrollPane fieldPane = new JScrollPane(field, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		gb.setConstraints(fieldPane, gc);
		add(fieldPane);
		gb.setConstraints(buttons, gc);
		add(buttons);
		gc.fill = GridBagConstraints.BOTH;
		gb.setConstraints(error, gc);
		add(error);

		myListener.insertUpdate(null);
		setError(null);
	}

	@Override
	public void copy() {
		field.requestFocus();
		field.copy();
	}

	@Override
	public void delete() {
		field.requestFocus();
		field.replaceSelection("");
	}

	String getCurrentVariable() {
		return selector.getSelectedOutput();
	}

	@Override
	void localeChanged() {
		selector.localeChanged();
		prettyView.localeChanged();
		clear.setText(Strings.get("exprClearButton"));
		revert.setText(Strings.get("exprRevertButton"));
		enter.setText(Strings.get("exprEnterButton"));
		if (errorMessage != null) {
			error.setText(errorMessage.get());
		}
	}

	@Override
	public void paste() {
		field.requestFocus();
		field.paste();
	}

	void registerDefaultButtons(DefaultRegistry registry) {
		registry.registerDefaultButton(field, enter);
	}

	@Override
	public void selectAll() {
		field.requestFocus();
		field.selectAll();
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