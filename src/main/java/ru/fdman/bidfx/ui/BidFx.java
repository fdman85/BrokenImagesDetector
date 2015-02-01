package ru.fdman.bidfx.ui;

import com.sun.javafx.collections.ObservableListWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import ru.fdman.bidfx.FileType;
import ru.fdman.bidfx.Status;
import ru.fdman.bidfx.process.BasicReportImpl;
import ru.fdman.bidfx.process.ProgressData;
import ru.fdman.bidfx.process.Report;
import ru.fdman.bidfx.process.ScanPerformer;
import ru.fdman.bidfx.process.processes.processor.algorithm.ByteAsImagesProcessAlgorithm;
import ru.fdman.bidfx.process.processes.processor.result.BytesProcessResult;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import java.awt.*;
import java.io.*;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Created by fdman on 24.06.2014.
 */
public class BidFx extends Application {
    //TODO only one app is allowed?
    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {

        SLF4JBridgeHandler.removeHandlersForRootLogger();  // (since SLF4J 1.6.5)
        SLF4JBridgeHandler.install();

        MainForm form = new MainForm(stage);

        form.init();

        FormController formController = new FormController(form);

        formController.setupComponentsBehavior();

        stage.setTitle(UIConstants.MAIN_TITLE);

        stage.getIcons().add(new Image("icons/com.iconfinder/tango-icon-library/1415555653_folder-saved-search-32.png"));

        stage.setIconified(true);

        Scene scene = new Scene(form.createMainGrid());

        formController.setMainFormScene(scene);

        scene.getStylesheets().add("css/style.css");

        stage.setScene(scene);


        stage.show();

        stage.setMinHeight(stage.getHeight());
        stage.setMinWidth(stage.getWidth());

    }

    //TODO grow(resize?) correctly
    private class MainForm {
        private final Stage stage;
        private Button scanBtn = new Button("Start scan");
        private CheckBox jpgCheckBox = new CheckBox(FileType.JPG.getExtensions()[0]);
        private CheckBox gifCheckBox = new CheckBox(FileType.GIF.getExtensions()[0]);
        private CheckBox nefCheckBox = new CheckBox(FileType.NEF.getExtensions()[0]);
        private CheckBox bidCheckBox = new CheckBox(FileType.BID.getExtensions()[0]);
        private TextField folderPath = new TextField();
        private Button selectPathBtn = new Button("...");
        private DirectoryChooser directoryChooser = new DirectoryChooser(); //use file chooser cause it is more flexible (show files in folders)
        private ComboBox<Status> statusFilterComboBox = new ComboBox<>(FXCollections.observableArrayList(getStatusFilterComboboxItems()));
        private ComboBox<Clause> clauseFilterComboBox = new ComboBox<>(new ObservableListWrapper<>(Arrays.asList(Clause.values())));
        private Button moveRenameBtn = new Button("Rename...");
        private Button debugBtn = new Button("debug");
        private TreeTableView treeTableView = new TreeTableView();
        private Label moveRenameInfoLabel = new Label("Please, select more meaningful status than SKIPPED for activating 'Rename' button");
        private Label statusBarText = new Label("Select folder and press scan button");
        private ProgressBar progressBar = new ProgressBar(Double.MIN_NORMAL);
        private Label progressBarProgress = new Label("0%");


        public MainForm(Stage stage) {
            this.stage = stage;
            init();
        }

        private void init() {
            moveRenameBtn.setDisable(true);
            moveRenameBtn.setVisible(false);
            moveRenameInfoLabel.setVisible(false);
            statusFilterComboBox.setValue(Status.OK);
            clauseFilterComboBox.setValue(Clause.EQUAL_OR_STRONGER);
            scanBtn.setMinWidth(90);
            moveRenameBtn.setMinWidth(90);
            treeTableView.getColumns().setAll(getTreeTableViewColumns());
            treeTableView.setShowRoot(true);
            progressBar.progressProperty().addListener(new ChangeListener<Number>() {
                private double maxProgressValue = 0;

                @Override
                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                    if (newValue.doubleValue() >= 0) {
                        if (newValue.doubleValue() <= Double.MIN_NORMAL) {
                            maxProgressValue = 0;
                        } else if (newValue.doubleValue() >= 1) {
                            maxProgressValue = 1;
                        } else {
                            maxProgressValue = Math.max(maxProgressValue, newValue.doubleValue());
                        }
                        progressBarProgress.setText("" + Precision.round(maxProgressValue * 100., 0) + "%");
                    }

                }
            });
        }

        private GridPane createMainGrid() {
            GridPane contentGrid = new GridPane();
            //contentGrid.setGridLinesVisible(true);
            setupStandardFormGrid(contentGrid, UIConstants.GAP_STD, UIConstants.INSETS_STD);

            Node extensionsHbox = getExtensionsHbox();
            Node treeTableViewVbox = getTreeTableViewVbox();
            Node statusBarGrid = getStatusBarGrid();

            contentGrid.add(extensionsHbox, 0, 0);
            contentGrid.add(getSelectPathHbox(), 3, 0);
            contentGrid.add(treeTableViewVbox, 0, 2, 4, 1);
            contentGrid.add(getFilterHbox(), 0, 1, 1, 1);
            contentGrid.add(getMoveRenameHbox(), 2, 1, 2, 1);
            contentGrid.add(statusBarGrid, 0, 3, 4, 1);


            contentGrid.setHgrow(extensionsHbox, Priority.ALWAYS);
            contentGrid.setVgrow(treeTableViewVbox, Priority.ALWAYS);
            contentGrid.setHgrow(statusBarGrid, Priority.ALWAYS);
            contentGrid.setVgrow(statusBarGrid, Priority.NEVER);
            return contentGrid;
        }

        private void setupStandardFormGrid(GridPane contentGrid, double gap, Insets insets) {
            contentGrid.setHgap(gap);
            contentGrid.setVgap(gap);
            contentGrid.setPadding(UIConstants.INSETS_STD);
            contentGrid.setAlignment(Pos.CENTER);
        }

        private Node getTreeTableViewVbox() {
            VBox vBox = new VBox(10, treeTableView);
            vBox.setVgrow(treeTableView, Priority.ALWAYS);
            vBox.setAlignment(Pos.CENTER_RIGHT);
            return vBox;
        }

        private Node getMoveRenameHbox() {
            HBox hbox = new HBox(10,
                    moveRenameInfoLabel,
                    moveRenameBtn);
            hbox.setAlignment(Pos.CENTER_RIGHT);
            return hbox;
        }

        private Node getStatusBarGrid() {
            GridPane statusBarGrid = new GridPane();
            setupStandardFormGrid(statusBarGrid, 16, new Insets(0, 0, 0, 0));
            statusBarGrid.setAlignment(Pos.TOP_LEFT);
            statusBarGrid.add(statusBarText, 0, 0, 1, 1);
            statusBarGrid.add(progressBar, 1, 0, 1, 1);
            statusBarGrid.add(progressBarProgress, 2, 0, 1, 1);
            progressBarProgress.setMaxWidth(40);
            progressBarProgress.setMinWidth(40);
            progressBarProgress.setPrefWidth(40);
            statusBarGrid.setHgrow(progressBar, Priority.NEVER);
            statusBarGrid.setHgrow(statusBarText, Priority.ALWAYS);
            statusBarGrid.setHgrow(progressBarProgress, Priority.NEVER);

            statusBarGrid.getColumnConstraints().addAll(
                    new ColumnConstraints(-1, -1, -1, Priority.ALWAYS, HPos.LEFT, true),
                    new ColumnConstraints(-1, -1, -1, Priority.NEVER, HPos.RIGHT, false),
                    new ColumnConstraints(-1, -1, -1, Priority.NEVER, HPos.RIGHT, false)
            );

            statusBarGrid.getRowConstraints().addAll(
                    new RowConstraints(-1, -1, -1, Priority.NEVER, VPos.CENTER, false)
            );

            return statusBarGrid;
        }

        private Node getExtensionsHbox() {
            Label extensionsLbl = new Label("Extensions: ");
            HBox extensionsHbox = new HBox(10, extensionsLbl, jpgCheckBox, gifCheckBox, nefCheckBox, bidCheckBox);
            extensionsHbox.setAlignment(Pos.CENTER_LEFT);
            return extensionsHbox;
        }

        private Node getSelectPathHbox() {
            Label selectScanFolderLbl = new Label("Select folder to scan: ");
            folderPath.setMinWidth(350);
            HBox selectPathHbox = new HBox(10, selectScanFolderLbl, folderPath, selectPathBtn, scanBtn/*, debugBtn*/);
            selectPathHbox.setAlignment(Pos.CENTER_RIGHT);
            return selectPathHbox;
        }

        private Node getFilterHbox() {
            Label filterLbl = new Label("Show results with status");
            HBox filterHbox = new HBox(10,
                    filterLbl,
                    clauseFilterComboBox,
                    statusFilterComboBox);
            filterHbox.setAlignment(Pos.CENTER_LEFT);
            return filterHbox;
        }

        private Collection<Status> getStatusFilterComboboxItems() {

            return Arrays.asList(Status.values()).
                    stream().
                    filter(status -> status != Status.SMTH_GOES_WRONG).
                    collect(Collectors.toList());
        }

        private Collection getTreeTableViewColumns() {
            List<TreeTableColumn<?, ?>> columns = new LinkedList<>();
            TreeTableColumn<BytesProcessResult, String> nameCol = new TreeTableColumn<>("Name");
            TreeTableColumn<BytesProcessResult, String> statusCol = new TreeTableColumn<>("Status");
            TreeTableColumn<BytesProcessResult, String> descriptionColumn = new TreeTableColumn<>("Description");
            TreeTableColumn<BytesProcessResult, String> detailsColumn = new TreeTableColumn<>("Details");

            nameCol.setPrefWidth(300);
            statusCol.setPrefWidth(100);
            descriptionColumn.setPrefWidth(450);
            detailsColumn.setPrefWidth(255);

            nameCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<BytesProcessResult, String> p) -> new ReadOnlyStringWrapper(p.getValue().getValue() == null ? "-" : p.getValue().getValue().getPath().toFile().getName()));
            statusCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue() == null || p.getValue().getValue().getStatus() == null || p.getValue().getValue().getStatus() == Status.FOLDER ? "" : p.getValue().getValue().getStatus().toString()));
            descriptionColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue() == null ? "-" : p.getValue().getValue().getDescription()));
            descriptionColumn.setCellFactory(new DescriptionAndDetailsCellFactory("File processing description"));
            detailsColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue() == null ? "-" : p.getValue().getValue().getDetails()));
            detailsColumn.setCellFactory(new DescriptionAndDetailsCellFactory("File processing details"));
            columns.add(nameCol);
            columns.add(statusCol);
            columns.add(descriptionColumn);
            columns.add(detailsColumn);
            return columns;
        }


        private class DescriptionAndDetailsCellFactory implements Callback<TreeTableColumn<BytesProcessResult, String>, TreeTableCell<BytesProcessResult, String>> {

            private final String title;

            public DescriptionAndDetailsCellFactory(String title) {
                this.title = title;
            }

            @Override
            public TreeTableCell<BytesProcessResult, String> call(TreeTableColumn<BytesProcessResult, String> param) {
                TreeTableCell<BytesProcessResult, String> cell = new LabeledWithButtonTreeTableCell();
                return cell;
            }

            private class LabeledWithButtonTreeTableCell extends TreeTableCell<BytesProcessResult, String> {
                @Override
                protected void updateItem(String item, boolean empty) {
                    if (!empty &&
                            !StringUtils.isBlank(item) &&
                            getTreeTableRow().getTreeItem() != null &&
                            getTreeTableRow().getTreeItem().getValue() != null) {
                        TreeItem<BytesProcessResult> treeItem = getTreeTableRow().getTreeItem();
                        Label lbl = new Label(item.replaceAll("\\s*[\\r\\n]+\\s*", "").trim());
                        Button button = new Button("...");
                        button.setMaxSize(lbl.getHeight() / 2, lbl.getHeight() / 2);
                        HBox hBox = new HBox(lbl, button);
                        button.getStyleClass().add("tree-table-row-bckgnd-with-status");
                        button.setOnAction(event -> {
                            getTreeTableView().getSelectionModel().select(treeItem);
                            String message = item.replaceAll("\\r\\n", "").trim();
                            message = message.length() > 2000 ? message.substring(0, 1997) + "...\n\n... U can copy to clipboard the full log" : message;
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle(title);
                            alert.setHeaderText(null);
                            alert.setContentText(treeItem.getValue().getPath().toString());


                            // Create expandable Exception.
                            Label label = new Label("The exception stacktrace was:");

                            TextArea textArea = new TextArea(message);
                            textArea.setEditable(false);
                            textArea.setWrapText(true);

                            textArea.setMaxWidth(Double.MAX_VALUE);
                            textArea.setMaxHeight(Double.MAX_VALUE);
                            GridPane.setVgrow(textArea, Priority.ALWAYS);
                            GridPane.setHgrow(textArea, Priority.ALWAYS);
                            GridPane expContent = new GridPane();
                            expContent.setMaxWidth(Double.MAX_VALUE);
                            expContent.add(label, 0, 0);
                            expContent.add(textArea, 0, 1);
                            alert.getDialogPane().setExpandableContent(expContent);
                            ButtonType buttonTypeCopy = new ButtonType("Copy details to clipboard");
                            ButtonType buttonTypeOk = new ButtonType("Ok", ButtonBar.ButtonData.CANCEL_CLOSE);

                            alert.getButtonTypes().setAll(buttonTypeCopy, buttonTypeOk);

                            final Button buttonCopy = (Button) alert.getDialogPane().lookupButton(buttonTypeCopy);
                            buttonCopy.addEventFilter(ActionEvent.ACTION, (e) -> {
                                final Clipboard clipboard = Clipboard.getSystemClipboard();
                                final ClipboardContent content = new ClipboardContent();
                                content.putString(treeItem.getValue().getPath().toString() + "\n" + item);
                                clipboard.setContent(content);
                                buttonCopy.setText("Copied");
                                e.consume();
                            });
                            switch (treeItem.getValue().getStatus()) {
                                case CRITICAL:
                                case ERROR:
                                    alert.setAlertType(Alert.AlertType.ERROR);
                                    break;
                                case WARN:
                                    alert.setAlertType(Alert.AlertType.WARNING);
                                    break;
                                default:
                                    alert.setAlertType(Alert.AlertType.INFORMATION);
                            }

                            Optional<ButtonType> result = alert.showAndWait();
                            if (result.get() == buttonTypeCopy) {
//                                final Clipboard clipboard = Clipboard.getSystemClipboard();
//                                final ClipboardContent content = new ClipboardContent();
//                                content.putString(treeItem.getValue().getPath().toString() + "\n" + item);
//                                clipboard.setContent(content);

                            }
                        });
                        setGraphic(hBox);
                    } else {
                        setText(null);
                        setGraphic(null);
                    }
                }
            }
        }
    }

    private class FormController {
        private final Logger log = LoggerFactory.getLogger(FormController.class);
        private final MainForm mainForm;
        private Scene scene;
        private FormConfigController formConfigController;
        private FormConfig formConfig;
        private ScanBtnEventHandler scanBtnEventHandler;
        private ResultsTreePostProcessor<TreeItem<BytesProcessResult>, BytesProcessResult> resultsTreePostProcessor;
        private Configuration freeMarkerCfg;

        private FormController(MainForm mainForm) {
            this.mainForm = mainForm;
            this.scanBtnEventHandler = new ScanBtnEventHandler();
            createOrRestoreFormConfig();
        }

        private void createOrRestoreFormConfig() {
            FormConfig formConfig = null;
            formConfigController = new FormConfigController();
            if (formConfigController.isBackingStoreAvailable()) {
                formConfig = formConfigController.restore();
            }
            this.formConfig = (formConfig == null ? new FormConfig() : formConfig);
        }


        private void setupComponentsBehavior() throws IOException, URISyntaxException {
            setupFreeMarkerEngine();
            setupMainTreeTableBehaviour();
            setupMainFormCloseBehavior();
            setupDebugBtnBehavior();
            setupFileChooserBehavior();
            setupFolderTextFieldBehavior();
            setupFileTypeCheckBoxesBehavior();
            setupScanBtnBehavior();
            setupFilterComboboxesBehaviour();
            setupMoveRenameBtnBehaviour();
        }

        private void setupFreeMarkerEngine() throws IOException, URISyntaxException {
            freeMarkerCfg = new Configuration(Configuration.VERSION_2_3_21);
            freeMarkerCfg.setDirectoryForTemplateLoading(new File(this.getClass().getClassLoader().getResource("freemarker").toURI()));
            //Template freemarkerTemplate = freeMarkerCfg.getTemplate("email/vendor.tpl");

            //freeMarkerCfg.setDirectoryForTemplateLoading(new File());
            freeMarkerCfg.setDefaultEncoding("UTF-8");
            freeMarkerCfg.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
        }

        private void setupMoveRenameBtnBehaviour() {
            mainForm.moveRenameBtn.setOnAction(new MoveRenameBtnEventHandler());
        }

        private void setupMainTreeTableBehaviour() {
            mainForm.treeTableView.setRowFactory(new Callback<TreeTableView, TreeTableRow>() {
                @Override
                public TreeTableRow call(TreeTableView param) {

                    TreeTableRow<BytesProcessResult> treeTableRow = new TreeTableRow<BytesProcessResult>() {
                        @Override
                        protected void updateItem(BytesProcessResult item, boolean empty) {
                            super.updateItem(item, empty);
                            setDisclosureNode(null);
                            getStyleClass().clear();
                            getStyleClass().add("my-tree-table-row-text-fill");
                            if (item != null) {
                                if (!StringUtils.isEmpty(item.getStatus().toString())) {
                                    getStyleClass().add("tree-table-row-bckgnd-with-status");
                                    setId("tree-table-row-bckgnd-" + item.getStatus().toString().toLowerCase());
                                } else {
                                    getStyleClass().add("tree-table-row-bckgnd-empty");
                                    setId(null);
                                }
                            } else {
                                getStyleClass().add("tree-table-row-bckgnd-empty");
                                setId(null);
                            }

                            disableStandardDoubleClick();
                        }

                        private void disableStandardDoubleClick() {
                            //Заменяем стандартное поведение по двойному клику на папке
                            final EventDispatcher originalEventDispatcher = getEventDispatcher();//Disable TreeItem's default expand/collapse on double click JavaFX 2.2
                            setEventDispatcher(new EventDispatcher() {
                                @Override
                                public javafx.event.Event dispatchEvent(javafx.event.Event event, EventDispatchChain tail) {
                                    if (!event.isConsumed() && event.getEventType() == MouseEvent.MOUSE_RELEASED && ((MouseEvent) event).getClickCount() >= 2
                                            && ((MouseEvent) event).getButton() == MouseButton.PRIMARY /*&& event.getSource() == treeTableView*/) {
                                        TreeItem selectedItem = (TreeItem) mainForm.treeTableView.getSelectionModel().getSelectedItem();
                                        if (selectedItem != null) {
                                            BytesProcessResult processResult = (BytesProcessResult) selectedItem.getValue();
                                            if (processResult != null && !processResult.isLeaf()) {
                                                //Do nothing on dbl click to folder in tree table grid
                                                event.consume();
                                            }
                                        }

                                    }
                                    return originalEventDispatcher.dispatchEvent(event, tail);
                                }
                            });
                        }
                    };
                    Calendar calendar = Calendar.getInstance();
                    if (calendar.get(Calendar.DATE) == 1 && calendar.get(Calendar.MONTH) == 3) //01.04 easter egg
                    {
                        treeTableRow.setRotate((Math.random() - 0.5) * 3);
                    }

                    return treeTableRow;
                }
            });


            mainForm.treeTableView.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        TreeItem selectedItem = (TreeItem) mainForm.treeTableView.getSelectionModel().getSelectedItem();
                        if (selectedItem != null) {
                            BytesProcessResult processResult = (BytesProcessResult) selectedItem.getValue();
                            if (processResult != null) {
                                if (event.getClickCount() >= 1 && !processResult.isLeaf()) {
                                    //expand/collapse folder
                                    selectedItem.setExpanded(!selectedItem.isExpanded());
                                } else if (event.getClickCount() >= 2 && processResult.getPath() != null && processResult.isLeaf()) {
                                    File f = processResult.getPath().toFile();
                                    if (f.exists()) {
                                        if (Desktop.isDesktopSupported()) {
                                            try {
                                                Desktop.getDesktop().browse(f.toURI());
                                            } catch (IOException e) {
                                                log.warn("Can`t read file. Exception: {}", ExceptionUtils.getMessage(e));
                                                try {
                                                    Desktop.getDesktop().browse(f.getParentFile().toURI());
                                                } catch (IOException e1) {
                                                    log.error("Can`t read parent file. Exception: {}", ExceptionUtils.getStackTrace(e));
                                                }
                                            }
                                        } else {
                                            log.warn("Desktop browsing is not supported by your OS JVM :(. Can`t open {} ", processResult.getPath().toString());
                                        }
                                    } else {
                                        log.warn("File {} is not found :(", f.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    }
                }
            });

        }

        private void setupFilterComboboxesBehaviour() {
            mainForm.clauseFilterComboBox.setConverter(new StringConverter<Clause>() {
                @Override
                public String toString(Clause object) {
                    switch (object) {
                        case EQUAL_OR_STRONGER:
                            return "equal or stronger than";
                        case EQUAL:
                            return "equal to";
                        default:
                            return null;
                    }
                }

                @Override
                public Clause fromString(String string) {
                    return Clause.valueOf(string);
                }
            });

            mainForm.statusFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue.getPriority() <= Status.SKIPPED.getPriority()) {
                    mainForm.moveRenameInfoLabel.setText("Please, select more meaningful status than SKIPPED for activating 'Rename' button");
                    mainForm.moveRenameBtn.setDisable(true);
                } else {
                    mainForm.moveRenameBtn.setDisable(false);
                    mainForm.moveRenameInfoLabel.setText("");
                }
                if (resultsTreePostProcessor != null) {
                    refreshTreeTableView();
                }

            });
            mainForm.clauseFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (resultsTreePostProcessor != null) {
                    refreshTreeTableView();
                }
            });
        }

        private void setupScanBtnBehavior() {
            File folder = new File(mainForm.folderPath.getText());
            if (folder.exists() && folder.isDirectory()) {
                mainForm.scanBtn.setDisable(false);
            } else {
                mainForm.scanBtn.setDisable(true);
            }
            mainForm.scanBtn.setOnAction(scanBtnEventHandler);
        }

        private void setupMainFormCloseBehavior() {
            mainForm.stage.setOnCloseRequest(event -> {
                //TODO some checks
                formConfigController.save(formConfig);
            });
        }

        private void setupDebugBtnBehavior() {
            mainForm.debugBtn.setOnAction(event -> {
                //nothing
            });
        }

        private void setupFileTypeCheckBoxesBehavior() {
            mainForm.jpgCheckBox.selectedProperty().bindBidirectional(formConfig.jpgCheckBoxSelectedStateProperty());
            mainForm.gifCheckBox.selectedProperty().bindBidirectional(formConfig.gifCheckBoxSelectedStateProperty());
            mainForm.nefCheckBox.selectedProperty().bindBidirectional(formConfig.nefCheckBoxSelectedStateProperty());
            mainForm.bidCheckBox.selectedProperty().bindBidirectional(formConfig.bidCheckBoxSelectedStateProperty());
        }

        private void setupFolderTextFieldBehavior() {

            mainForm.folderPath.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && checkIsValidFolder(new File(newValue))) {
                    mainForm.scanBtn.setDisable(false);
                } else {
                    mainForm.scanBtn.setDisable(true);
                }
            });
            mainForm.folderPath.textProperty().bindBidirectional(formConfig.getFolderPathProperty());
        }

        private void setupFileChooserBehavior() {
            mainForm.directoryChooser.setTitle("Select folder to scan");
            mainForm.selectPathBtn.setOnAction(event -> {
                //update init directory
                if (mainForm.folderPath.getText() != null) {
                    File prevSelectedFolder = new File(mainForm.folderPath.getText());
                    if (checkIsValidFolder(prevSelectedFolder)) {
                        mainForm.directoryChooser.setInitialDirectory(prevSelectedFolder);
                    }
                }
                //show and check selected
                File selectedFolder = mainForm.directoryChooser.showDialog(mainForm.stage);

                if (selectedFolder != null) {
                    if (checkIsValidFolder(selectedFolder)) {
                        mainForm.folderPath.setText(selectedFolder.getAbsolutePath());
                    } else {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setHeaderText(null);
                        alert.setContentText("Please, select a valid folder.\nYou must to have read permissions on it.");
                        alert.show();
                    }
                }
            });
        }

        private boolean checkIsValidFolder(File folder) {
            if (folder != null && folder.exists() && folder.isDirectory() && folder.canRead()) {
                return true;
            }
            return false;
        }

        private Set<FileType> getSelectedFileTypes() {
            Set<FileType> result = new HashSet<>();
            if (mainForm.jpgCheckBox.isSelected()) {
                result.add(FileType.JPG);
            }
            if (mainForm.gifCheckBox.isSelected()) {
                result.add(FileType.GIF);
            }
            if (mainForm.nefCheckBox.isSelected()) {
                result.add(FileType.NEF);
            }
            if (mainForm.bidCheckBox.isSelected()) {
                result.add(FileType.BID);
            }
            return result;
        }

        public void setMainFormScene(Scene scene) {
            this.scene = scene;
        }


        private class TreeTableRowMouseEventHandler implements EventHandler<MouseEvent> {
            private final Logger log;
            private final TreeTableRow<BytesProcessResult> treeTableRow;

            public TreeTableRowMouseEventHandler(TreeTableRow<BytesProcessResult> treeTableRow) {
                this.treeTableRow = treeTableRow;
                log = LoggerFactory
                        .getLogger(getClass());
            }

            @Override
            public void handle(MouseEvent event) {
                if (treeTableRow.getIndex() < 0) {
                    return;
                }

                if (event.getClickCount() != 2) {
                    return;
                }
                BytesProcessResult processResult = treeTableRow.getItem();
                if (processResult.getPath() != null) {
                    File f = processResult.getPath().toFile();
                    if (f.exists()) {
                        if (Desktop.isDesktopSupported()) {
                            try {
                                Desktop.getDesktop().browse(f.toURI());
                            } catch (IOException e) {
                                log.warn("Can`t read file. Exception: {}", ExceptionUtils.getMessage(e));
                                try {
                                    Desktop.getDesktop().browse(f.getParentFile().toURI());
                                } catch (IOException e1) {
                                    log.error("Can`t read parent file. Exception: {}", ExceptionUtils.getStackTrace(e));
                                }
                            }
                        } else {
                            log.warn("Desktop browsing is not supported by your OS JVM :(. Can`t open {} ", processResult.getPath().toString());
                        }
                    } else {
                        log.warn("File {} is not found :(", f.getAbsolutePath());
                    }
                }
            }
        }

        private class ScanBtnEventHandler implements EventHandler<ActionEvent> {
            private final Logger log = LoggerFactory
                    .getLogger(ScanBtnEventHandler.class);
            private ScanPerformer scanPerformer;
            private volatile boolean scanning = false;

            @Override
            public void handle(ActionEvent event) {
                if (scanning) {
                    scanPerformer.pauseScan();
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Confirm");
                        alert.setHeaderText(null);
                        alert.setContentText("Scan is in progress. Cancel?");

                        ButtonType buttonTypeYes = new ButtonType("Yes");
                        ButtonType buttonTypeCancel = new ButtonType("No, continue scanning", ButtonBar.ButtonData.CANCEL_CLOSE);

                        alert.getButtonTypes().setAll(buttonTypeYes, buttonTypeCancel);
                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.get() == buttonTypeYes) {
                            scanPerformer.cancelScan();
                            //mainForm.scanBtn.setText("Start scan");
                            setUIDisabled(false);
                            scanning = false;
                        } else {

                            scanPerformer.unpauseScan();
                            scanning = true;
                        }
                    });
                } else {

                    scanning = true;
                    Report report = new BasicReportImpl();
                    Platform.runLater(() -> setUIDisabled(true)
                    );

                    scanPerformer = new ScanPerformer(mainForm.folderPath.getText(),
                            getSelectedFileTypes(),
                            ByteAsImagesProcessAlgorithm.class,
                            report,
                            (aVoid) -> {
                                log.info("Scan finished");
                                scanning = false;
                                Platform.runLater(() -> {
                                    setNewResultTreePostProcessor(report);
                                    refreshTreeTableView();
                                    setUIDisabled(false);
                                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                    alert.setHeaderText(null);
                                    alert.setContentText("Scan completed");
                                    mainForm.progressBar.setProgress(1);
                                    alert.show();
                                });
                            },
                            (aVoid) -> {
                                log.info("Scan cancelled");
                                scanning = false;
                                Platform.runLater(() -> {
                                    setUIDisabled(false);
                                    setNewResultTreePostProcessor(report);
                                    refreshTreeTableView();
                                    if (mainForm.progressBar.getProgress() < 0) {
                                        mainForm.progressBar.setProgress(Double.MIN_NORMAL);
                                    }
                                });
                            },
                            new Consumer<ProgressData>() {
                                //tricky hack with progress calculations
                                private double maxProgressValue = -1d;

                                @Override
                                public void accept(ProgressData aProgressData) {
                                    Platform.runLater(() -> {
                                        maxProgressValue = Math.max(aProgressData.getTotal(), maxProgressValue);
                                        double progress = Precision.round(((maxProgressValue - aProgressData.getTotal()) / maxProgressValue), 2);
                                        if (!Double.isNaN(progress) && progress > 0) {
                                            mainForm.progressBar.setProgress(progress + 0.05);
                                        } else {
                                            mainForm.progressBar.setProgress(-1);
                                        }
                                        mainForm.statusBarText.setText(aProgressData.getInfo());
                                    });
                                }
                            }
                    );
                    scanPerformer.performScan();
                    mainForm.progressBar.setProgress(Double.MIN_NORMAL);
                }
            }

            private void setUIDisabled(boolean disable) {
                if (disable) {
                    mainForm.scanBtn.setText("Cancel scan");
                } else {
                    mainForm.scanBtn.setText("Start scan");
                }
                mainForm.folderPath.setDisable(disable);
                mainForm.selectPathBtn.setDisable(disable);
                mainForm.gifCheckBox.setDisable(disable);
                mainForm.bidCheckBox.setDisable(disable);
                mainForm.jpgCheckBox.setDisable(disable);
                mainForm.nefCheckBox.setDisable(disable);
                mainForm.clauseFilterComboBox.setDisable(disable);
                mainForm.statusFilterComboBox.setDisable(disable);
                mainForm.moveRenameInfoLabel.setVisible(!disable);
                mainForm.moveRenameBtn.setVisible(!disable);
            }


            private void setNewResultTreePostProcessor(Report report) {
                List<BytesProcessResult> copiedReportLines = new ArrayList<>(report.getLines());//prevent concurrent exception if some thread will not stop for some reason
                log.info("Files scanned: {}", copiedReportLines.size());
                ResultsTreeBuilder resultsTreeBuilder = new ResultsTreeBuilder(mainForm.folderPath.getText());
                TreeItem treeItem = resultsTreeBuilder.generateTree(copiedReportLines);
                resultsTreePostProcessor = new ResultsTreePostProcessor<>(treeItem);

            }
        }

        private void refreshTreeTableView() {
            if (resultsTreePostProcessor != null) {
                TreeItem<BytesProcessResult> tmpForViewRoot = resultsTreePostProcessor.shrinkTree(resultsTreePostProcessor.getRoot());
                resultsTreePostProcessor.sortAndFilterTree(tmpForViewRoot,
                        bytesProcessResultTreeItem -> bytesProcessResultTreeItem.getChildren().sort((o1, o2) -> {
                            if (o1.isLeaf() && !o2.isLeaf()) {
                                return 1;
                            } else if (!o1.isLeaf() && o2.isLeaf()) {
                                return -1;
                            } else {
                                return o1.getValue().getPath().compareTo(o2.getValue().getPath());
                            }
                        }),
                        bytesProcessResultTreeItem -> {
                            if (!bytesProcessResultTreeItem.getChildren().isEmpty()) {
                                return false; //don`t filter if contains children
                            }
                            if (mainForm.clauseFilterComboBox.getSelectionModel().getSelectedItem().equals(Clause.EQUAL)) {
                                bytesProcessResultTreeItem.getValue().getStatus().equals(mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem());
                                if (!bytesProcessResultTreeItem.getValue().getStatus().equals(mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem())) {
                                    //log.trace("FILTERED by equality| bytesProcessResultTreeItem.getValue().getStatus() {} mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem() {}", bytesProcessResultTreeItem.getValue().getStatus(), mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem());
                                    return true;
                                }
                            } else if (mainForm.clauseFilterComboBox.getSelectionModel().getSelectedItem().equals(Clause.EQUAL_OR_STRONGER)) {
                                if (bytesProcessResultTreeItem.getValue().getStatus().getPriority() < mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem().getPriority()) {
                                    //log.trace("FILTERED by priority| bytesProcessResultTreeItem.getValue().getStatus() {} mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem() {}", bytesProcessResultTreeItem.getValue().getStatus(), mainForm.statusFilterComboBox.getSelectionModel().getSelectedItem());
                                    return true;
                                }
                            }
                            return false;
                        });
                tmpForViewRoot.setExpanded(true);
                mainForm.treeTableView.setRoot(tmpForViewRoot);
            }

        }

        private class MoveRenameBtnEventHandler implements EventHandler<ActionEvent> {

            private List<String> renamedTotalFilesList;
            private List<String> notRenamedTotalFilesList;
            private TreeItem<BytesProcessResult> root;
            private LocalDateTime localDateTime;
            private String dateTimeForReportFileName;
            private String dateTimeForReportText;
            private String totalReportName;

            @Override
            public void handle(ActionEvent event) {
                if (mainForm.treeTableView.getRoot() == null) {
                    Alert alertOops = new Alert(Alert.AlertType.INFORMATION);
                    alertOops.setTitle("Ooops!");
                    alertOops.setHeaderText(null);
                    alertOops.setContentText("Please, do scan before");
                    alertOops.show();
                    return;
                }

                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Please, confirm");
                confirmAlert.setHeaderText(null);
                confirmAlert.setContentText("All not *.bid files at the table will be renamed with \"<status>.bid\" postfix. Continue?");
                ButtonType buttonTypeOne = new ButtonType("Yes");
                ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirmAlert.getButtonTypes().setAll(buttonTypeOne, buttonTypeCancel);

                Optional<ButtonType> result = confirmAlert.showAndWait();
                if (result.get() == buttonTypeOne) {
                    localDateTime = LocalDateTime.now();
                    dateTimeForReportFileName = DateTimeFormatter.ofPattern("YYYYMMdd_HHmmss").format(localDateTime);
                    dateTimeForReportText = DateTimeFormatter.ofPattern("dd MMM YYYY HH:mm:ss").format(localDateTime);
                    root = mainForm.treeTableView.getRoot();
                    totalReportName = root.getValue().getPath().toAbsolutePath().toString() + File.separator
                            + "~BID total report "
                            + dateTimeForReportFileName
                            + ".txt";
                    renamedTotalFilesList = new ArrayList<>();
                    notRenamedTotalFilesList = new ArrayList<>();
                    iterateTreeAndRename(root);
                    makeTotalReportAndStore();
                    refreshTreeTableView();
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setHeaderText(null);
                        alert.setContentText(renamedTotalFilesList.size() > 0 ? renamedTotalFilesList.size() + " files was renamed" : "Files were not renamed");
                        alert.setTitle(renamedTotalFilesList.size() > 0 ? "Renamed files list" : "Information");
                        alert.show();
                    });


                } else {
                    // ... user chose CANCEL or closed the dialog
                }

            }

            private void iterateTreeAndRename(final TreeItem<BytesProcessResult> parentTreeItem) {
                ObservableList<TreeItem<BytesProcessResult>> children = parentTreeItem.getChildren();
                List<String> renamedAtCurrentLevelFilesList = new ArrayList<>();
                for (TreeItem<BytesProcessResult> childItem : children) {
                    iterateTreeAndRename(childItem);

                    BytesProcessResult bytesProcessResult = childItem.getValue();
                    String renamedFileName = getNewFileName(bytesProcessResult);
                    if (isNeedToRename(bytesProcessResult) && renameFile(bytesProcessResult.getPath().toFile(), renamedFileName)) {
                        childItem.getValue().setPath(new File(renamedFileName).toPath());
                        renamedAtCurrentLevelFilesList.add(renamedFileName);
                        renamedTotalFilesList.add(bytesProcessResult.getPath().toAbsolutePath().toString() + " renamed to " + FilenameUtils.getName(renamedFileName));
                        log.trace("{} renamed to {}", bytesProcessResult.getPath().toAbsolutePath().toString(), FilenameUtils.getName(renamedFileName));
                    } else {
                        if (bytesProcessResult.getStatus() != Status.FOLDER) {
                            notRenamedTotalFilesList.add(bytesProcessResult.getPath().toAbsolutePath().toString() + " was not renamed");
                            log.trace("{} was not renamed", bytesProcessResult.getPath().toAbsolutePath().toString());
                        }
                    }
                }
                makeFolderReportAndStore(renamedAtCurrentLevelFilesList, parentTreeItem.getValue().getPath().toAbsolutePath().toString());
            }

            private void makeFolderReportAndStore(List<String> renamedFilesList, String reportFolder) {
                if (renamedFilesList.size() > 0) {
                    Template template;
                    try {
                        template = freeMarkerCfg.getTemplate("folderReportEn.ftl");
                    } catch (IOException e) {
                        log.error("{}", ExceptionUtils.getStackTrace(e));
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("appVersionTitle", UIConstants.MAIN_TITLE);
                    data.put("currentDir", reportFolder);
                    data.put("dateTime", "" + dateTimeForReportText);

                    data.put("renamingInfo", notRenamedTotalFilesList.size() > 0 ? "Renamed " + renamedTotalFilesList.size() + " file(-s) at " + dateTimeForReportText : "");
                    data.put("files", renamedFilesList);

                    data.put("fullReportPathAndName", totalReportName);

                    String fileName = reportFolder + File.separator
                            + "~BID report "
                            + this.dateTimeForReportFileName
                            + ".txt";
                    processFreeMarkerTemplate(template,
                            fileName, data);
                }

            }

            private void makeTotalReportAndStore() {
                if (renamedTotalFilesList.size() > 0 || notRenamedTotalFilesList.size() > 0) {
                    Template template;
                    try {
                        template = freeMarkerCfg.getTemplate("totalReportEn.ftl");
                    } catch (IOException e) {
                        log.error("{}", ExceptionUtils.getStackTrace(e));
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("appVersionTitle", UIConstants.MAIN_TITLE);
                    data.put("dateTime", dateTimeForReportText);
                    data.put("rootDir", root.getValue().getPath().toAbsolutePath());

                    data.put("renamingInfo", renamedTotalFilesList.size() > 0 ? "Renamed " + renamedTotalFilesList.size() + " file(-s) at " + dateTimeForReportText : "");
                    data.put("okFiles", renamedTotalFilesList);

                    data.put("renamingErrors", notRenamedTotalFilesList.size() > 0 ? notRenamedTotalFilesList.size() + " files were not renamed" : "");
                    data.put("errFiles", notRenamedTotalFilesList);

                    processFreeMarkerTemplate(template,
                            totalReportName, data);
                }

            }

            private void processFreeMarkerTemplate(Template template, String fileName, Map<String, Object> data) {
                try (Writer fileWriter = new FileWriter(new File(fileName));) {
                    template.process(data, fileWriter);
                    fileWriter.flush();
                } catch (IOException | TemplateException e) {
                    log.error("{}", ExceptionUtils.getStackTrace(e));
                }
            }

            private boolean isNeedToRename(BytesProcessResult processResult) {
                if (processResult.isLeaf()) {
                    File file = processResult.getPath().toFile();
                    if (file.exists() && !file.isDirectory() && !FilenameUtils.getExtension(file.getName()).toUpperCase().equals("BID")) {
                        return true;
                    }
                }
                return false;
            }

            private String getNewFileName(BytesProcessResult processResult) {
                return processResult.getPath().toFile().getAbsolutePath() + "." + processResult.getStatus().toString().toLowerCase() + ".bid";
            }

            private boolean renameFile(File oldFile, String newFileName) {
                try {
                    oldFile.renameTo(new File(newFileName));
                    return true;
                } catch (Exception e) {
                    log.error("Renaming of {} exception {}", oldFile.getAbsoluteFile().toString(), ExceptionUtils.getStackTrace(e));
                }
                return false;
            }
        }
    }

}


class UIConstants {
    private static final String APP_NAME = "Broken Images Detector Fx";
    public static final String VERSION = "0.1";
    public static final String STATE = "Developer version";
    public static final String MAIN_TITLE = APP_NAME + " " + VERSION + (StringUtils.isBlank(STATE) ? "" : " ") + STATE;
    public static final Insets INSETS_STD = new Insets(5, 5, 5, 5);
    public static final double GAP_STD = 10;
}

@XmlRootElement
class FormConfig implements Serializable {
    private BooleanProperty jpgCheckBoxSelectedState = new SimpleBooleanProperty();
    private BooleanProperty gifCheckBoxSelectedState = new SimpleBooleanProperty();
    private BooleanProperty nefCheckBoxSelectedState = new SimpleBooleanProperty();
    private BooleanProperty bidCheckBoxSelectedState = new SimpleBooleanProperty();
    private StringProperty folderPath = new SimpleStringProperty("Select a folder to start scan");

    public boolean getJpgCheckBoxSelectedState() {
        return jpgCheckBoxSelectedState.get();
    }

    public BooleanProperty jpgCheckBoxSelectedStateProperty() {
        return jpgCheckBoxSelectedState;
    }

    public void setJpgCheckBoxSelectedState(boolean jpgCheckBoxSelectedState) {
        this.jpgCheckBoxSelectedState.set(jpgCheckBoxSelectedState);
    }

    public boolean getGifCheckBoxSelectedState() {
        return gifCheckBoxSelectedState.get();
    }

    public BooleanProperty gifCheckBoxSelectedStateProperty() {
        return gifCheckBoxSelectedState;
    }

    public void setGifCheckBoxSelectedState(boolean gifCheckBoxSelectedState) {
        this.gifCheckBoxSelectedState.set(gifCheckBoxSelectedState);
    }

    public boolean getNefCheckBoxSelectedState() {
        return nefCheckBoxSelectedState.get();
    }

    public BooleanProperty nefCheckBoxSelectedStateProperty() {
        return nefCheckBoxSelectedState;
    }

    public void setNefCheckBoxSelectedState(boolean nefCheckBoxSelectedState) {
        this.nefCheckBoxSelectedState.set(nefCheckBoxSelectedState);
    }

    public boolean getBidCheckBoxSelectedState() {
        return bidCheckBoxSelectedState.get();
    }

    public BooleanProperty bidCheckBoxSelectedStateProperty() {
        return bidCheckBoxSelectedState;
    }

    public void setBidCheckBoxSelectedState(boolean bidCheckBoxSelectedState) {
        this.bidCheckBoxSelectedState.set(bidCheckBoxSelectedState);
    }


    public String getFolderPath() {
        return folderPath.get();
    }

    public void setFolderPath(String folderPathValue) {
        folderPath.set(folderPathValue);
    }

    public StringProperty getFolderPathProperty() {
        return folderPath;
    }

}

class FormConfigController {
    private static final String BACKING_STORE_AVAIL = "BackingStoreAvailableTest";
    public static final String PREFERENCES = "bidfxuipreferences";

    public boolean isBackingStoreAvailable() {
        Preferences prefs = Preferences.userRoot().node("");
        try {
            boolean oldValue = prefs.getBoolean(BACKING_STORE_AVAIL, false);
            prefs.putBoolean(BACKING_STORE_AVAIL, !oldValue);
            prefs.flush();
        } catch (BackingStoreException e) {
            return false;
        }
        return true;
    }

    public void save(FormConfig formConfig) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            JAXBContext ctx = JAXBContext.newInstance(FormConfig.class);

            Marshaller m = ctx.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.marshal(formConfig, baos);
            baos.flush();
            Preferences prefs = Preferences.userNodeForPackage(BidFx.class);
            prefs.putByteArray(PREFERENCES, baos.toByteArray());
            prefs.flush();
        } catch (JAXBException | IOException | BackingStoreException e) {
            e.printStackTrace();
        }

    }

    public FormConfig restore() {
        FormConfig formConfig = null;
        Preferences prefs = Preferences.userNodeForPackage(BidFx.class);
        if (prefs != null) {
            byte[] restoredSettings = null;
            restoredSettings = prefs.getByteArray(PREFERENCES, restoredSettings);
            if (restoredSettings != null) {
                try {
                    JAXBContext readCtx = JAXBContext.newInstance(FormConfig.class);
                    Unmarshaller um = readCtx.createUnmarshaller();
                    formConfig = (FormConfig) um.unmarshal(new ByteArrayInputStream(restoredSettings));
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            }
        }
        return formConfig;
    }
}


enum Clause {
    EQUAL,
    EQUAL_OR_STRONGER;
}