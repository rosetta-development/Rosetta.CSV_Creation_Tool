package com.exlibris.dps.createRosettaCSV;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

class LogWindow extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private JTextArea textArea = new JTextArea();

	public LogWindow() {
		super("");
		setSize(850, 300);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		add(new JScrollPane(textArea));
		setVisible(true);
	}

	public void showInfo(String data) {
		textArea.append(data);
		this.validate();
	}
}