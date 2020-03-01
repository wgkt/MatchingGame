import java.awt.BorderLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFrame;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;

public class Main extends Application implements Runnable{
	
	private final String ip = "localhost";
	private final int port = 22222;
	private final Thread thread;
	
	
	private Socket socket;
	private ServerSocket serverSocket;
	private DataOutputStream outputTurn;
	private DataInputStream inputTurn;
	private ObjectOutputStream outputBoard;
	private ObjectInputStream inputBoard;
	
	private boolean isServer = false;
	private boolean clientConnected = false;
	
	private boolean lostConnection = false;
	private int numErrors = 0;
	
	
	private boolean myTurn = false;
	private final String[] wordList;
	private String[] board;
	
	private ToggleButton selected1;
	private ToggleButton selected2;
	private int myMatches = 0;
	private int opMatches = 0;
	
	private boolean gameOver = false;
	private boolean gameStateUpdate = false;
	private ToggleButton lastTileSelected;
	
	private final int numCols;
	private final int numRows;
	private TilePane tilePane;
	private Stage stage;
	private final double TILE_SIZE = 100;
	
	public Main() {
		wordList = new String[]{"dog","cat","cow","sloth","lion","zebra","bunny","hippo"};
		
		numCols = (int)Math.ceil(Math.sqrt(wordList.length*2));
		numRows = numCols;
		
		//Check for server
		if (!connectToServer()) {
			initServer();
			
			//Create board
			HashMap<String, Integer>  wordCount = new HashMap<String, Integer>();
			for (String s : wordList) {
				wordCount.put(s,0);
			}
			
			board = new String[2*wordList.length];
			for (int i = 0; i < board.length; i++) {
				String word = wordList[(int)Math.random()*wordList.length];
				while (wordCount.get(word) >= 2) {
					word = wordList[(int) Math.random()*wordList.length];
				}
				wordCount.put(word, wordCount.get(word)+1);
				board[i] = word;
			}
			//Wait for client
		} else {
			
			
			fetchBoardStateFromServer();
		}
		
		thread = new Thread(this, "demo");
		thread.start();
		
	}
	
	
	
	private boolean connectToServer() {
		System.out.println("\n trying to connect");
		try {
			socket = new Socket(ip, port);
			outputTurn = new DataOutputStream(socket.getOutputStream());
			inputTurn = new DataInputStream(socket.getInputStream());
		 
			outputBoard = new ObjectOutputStream(socket.getOutputStream());
			inputBoard = new ObjectInputStream(socket.getInputStream());
	
			
			clientConnected = true;
					isServer = false;
			System.out.println(">Successfully connected");
			
		} catch (Exception e) {
			return false;
		} return true;

	}

	private void initServer() {
		System.out.println("\nInitiliazing server");
		try {
			serverSocket = new ServerSocket(port, 8, InetAddress.getByName(ip));
			
			myTurn = true;
			isServer = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		launch(args);
		Main main = new Main();
	}
	
	
	public void run() {
		while (!gameOver) {
			gameTick();
			if (isServer && !clientConnected) {
				listenForServerRequest();
			}
		}
	}

	public void start(Stage primaryStage) throws Exception {
		this.stage = primaryStage;
		
		tilePane = new TilePane();
		tilePane.setPrefColumns(numCols);
		tilePane.setPrefColumns(numRows);
		
		for (int i = 0; i < board.length; i++) {
			 tilePane.getChildren().add(createButton(i));
		}
		
		disableTiles(true);
		
		StackPane root = new StackPane();
		root.getChildren().add(tilePane);
		
		Scene scene = new Scene(root, numCols*TILE_SIZE, numRows*TILE_SIZE);
		scene.getStylesheets().add(this.getClass().getResource("Style.css").toExternalForm());
		
		if (isServer) {
			addStringToTitle("Waiting for client");
		} else {
			addStringToTitle("Connected");
		}
		
		this.stage.setScene(scene);
		this.stage.show();
	
	}





	private void updateBoardStateOnTileSelection(){
		ToggleButton selectedTile = null;
		 if (myTurn && gameStateUpdate) {
			 gameStateUpdate = false;
			 
			 selectedTile = lastTileSelected;
			 
			 selectedTile.setDisable(true);
			 
			 try {
				 outputTurn.writeInt(getIndexOfTile(selectedTile));
			     outputTurn.flush();
			 } catch (Exception e) {
				 e.printStackTrace();
				 numErrors++;
			 }
		 }
		 
		 if (!myTurn) {
			 try {
				 int tileIndex = inputTurn.readInt();
				 selectedTile = (ToggleButton) tilePane.getChildren().get(tileIndex);
				 selectedTile.setSelected(true);
			 } catch (IOException e) {
				 e.printStackTrace();
				 numErrors++;
			 }
		 }
		 if (selected1 == null) {
			 selected1 = selectedTile;
		 } else {
			 selected2 = selectedTile;
		 }
		 
		 if (selected1 != null && selected2 != null) {
			 checkForMatch();
		 }
		 
	}


	private void checkGameOver() {
		if (myMatches + opMatches != wordList.length) {
			gameOver = false;
		} else {
			gameOver = true;
			if (myMatches > opMatches) {
				addStringToTitle("Winnder!");
			} else if (myMatches == opMatches){
				addStringToTitle("Draw!");
			} else {
				addStringToTitle("Loser");
			}
		}
	}



	private void disableTiles(boolean disableOrNot) {
		for (Node child : tilePane.getChildren()) {
			if (child instanceof ToggleButton) {
				ToggleButton tile = (ToggleButton)child;
				
				if(tile.isSelected() == false) {
					tile.setDisable(disableOrNot);
				}
			}
		}
	}


	private int getIndexOfTile(ToggleButton b) {
		int index = 0;
		for (Node n : tilePane.getChildren()) {
			if (n.equals(b)) {
				return index;
			}
			index++;
		}
		return -1;
	}
	


	private void checkForMatch() {
		disableTiles(true);
		
		if (selected1.getText().equals(selected2.getText())) {
			if (myTurn) {
				disableTiles(false);
			}
			highlightMyMatch(myTurn);
			
			checkGameOver();
			
		} else {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			selected1.setSelected(false);
			selected2.setSelected(false);

			myTurn = !myTurn;
			disableTiles(!myTurn);
			if (myTurn) {
				addStringToTitle("My turn");
			} else{ 
				addStringToTitle("Opponent's turn");
			}
		}
		selected1 = null;
		selected2 = null;
		
	}




	private void listenForServerRequest() {
        Socket socket = null;
        try {
        	socket = serverSocket.accept();
			outputTurn = new DataOutputStream(socket.getOutputStream());
			inputTurn = new DataInputStream(socket.getInputStream());
		 
			outputBoard = new ObjectOutputStream(socket.getOutputStream());
			inputBoard = new ObjectInputStream(socket.getInputStream());
	
			
			clientConnected = true;
			isServer = true;
			myTurn = true;
        	
			
			sendClientBoardState();
			
			disableTiles(false);
        
        } catch (IOException e) {
        	e.printStackTrace();
        }
	
	}

	
	private void sendClientBoardState() {
		try {
			outputBoard.writeObject(board);
			outputBoard.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
			numErrors++;
		}
	}
	private void fetchBoardStateFromServer() {
		board = new String[2^wordList.length];
		try {
			String[] fromServer = (String[]) inputBoard.readObject();
			board = fromServer.clone();
		} catch(IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	

	
	

		
		private ToggleButton createButton(int index) {
			ToggleButton btn = new ToggleButton(board[index]);
			btn.setPrefHeight(TILE_SIZE);
			btn.setPrefWidth(TILE_SIZE);
			
			btn.setOnAction(new EventHandler<ActionEvent>() {
				public void handle(ActionEvent e) {
					ToggleButton source = (ToggleButton) e.getSource();
					lastTileSelected = source;
					gameStateUpdate = true;
				}
			});
			return btn;
		}
		
		
	
	
	
private void addStringToTitle(String s) {
	String clientServer = "Server";
	if (!isServer) {
		clientServer = "client";
	}
	if (this.stage != null) {
		this.stage.setTitle(clientServer+" : " + s);
	}
}

private void highlightMyMatch(boolean myMatch) {
	selected1.getStyleClass().clear();
	selected2.getStyleClass().clear();
	if (myMatch) {
		selected1.getStyleClass().add("my-match");
		selected2.getStyleClass().add("my-match");
		myMatches++;
	}else {
		selected1.getStyleClass().add("op-match");
		selected2.getStyleClass().add("op-match");
		opMatches++;
		
		
	}
}



private void gameTick() {
	if (numErrors > 10) {
		lostConnection = true;
		addStringToTitle("lost connection");
	}
	
	if (myTurn) {
		addStringToTitle("My turn");
	} else {
		addStringToTitle("Opponent's turn");
	}
	
	updateBoardStateOnTileSelection();
	
}

}