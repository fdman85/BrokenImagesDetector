package ru.fdman.bidfx.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.fdman.bidfx.process.processes.processor.result.BytesProcessResult;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Created by fdman on 07.10.2014.
 */
public class ResultsTreePostProcessor<T extends TreeItem<R>, R extends BytesProcessResult> {
    private final ExpandedPropertyListener expandedPropertyListener = new ExpandedPropertyListener();
    private final Logger log = LoggerFactory
            .getLogger(ResultsTreePostProcessor.class);
    private final T root;

    public ResultsTreePostProcessor(T root) {
        this.root = root;
    }

    public T shrinkTree(T root) {
        T newRoot = cloneTreeItem(root);
        return cloneTree(root, newRoot);
    }

    private T cloneTreeItem(T oldTreeItem) {
        T newTreeItem = (T) new TreeItem();
        newTreeItem.setValue(oldTreeItem.getValue());
        newTreeItem.setGraphic(oldTreeItem.getGraphic());
        newTreeItem.setExpanded(oldTreeItem.isExpanded());
        newTreeItem.expandedProperty().addListener(expandedPropertyListener);
        return newTreeItem;
    }

    public void sortAndFilterTree(final T parentTreeItem, final Consumer<T> sortTreeCallback,
                                  Predicate<T> filterValuesCallback) {
        sortTreeCallback.accept(parentTreeItem);
        ObservableList<T> children = (ObservableList<T>) parentTreeItem.getChildren();
        children.removeAll(children.filtered(filterValuesCallback));
        for (T childItem : children) {
            sortAndFilterTree(childItem, sortTreeCallback, filterValuesCallback);
        }
    }

    /**
     * Clone tree by starting from current root
     */
    private T cloneTree(final T oldParentTreeItem, T newParentTreeItem) {
        //oldParentTreeItem.getChildren().sort((o1, o2) -> o1.getValue().getPath().compareTo(o2.getValue().getPath()));
        ObservableList<T> children = (ObservableList<T>) oldParentTreeItem.getChildren();
        for (T oldChildItem : children) {
            T newChildTreeItem = cloneTreeItem(oldChildItem);
            cloneTree(oldChildItem, newChildTreeItem);
            newParentTreeItem.getChildren().add(newChildTreeItem);
        }
        return newParentTreeItem;
    }

    public T getRoot() {
        return root;
    }

    private class ExpandedPropertyListener implements ChangeListener {
        @Override
        public void changed(ObservableValue observable, Object oldValue, Object newValue) {
            BooleanProperty booleanProperty = (BooleanProperty) observable;
            TreeItem t = (TreeItem) booleanProperty.getBean();
            if ((Boolean)newValue) {
                t.setGraphic(new ImageView(
                        "icons/com.iconfinder/tango-icon-library/1415555509_folder-open-20.png"));
            } else {
                t.setGraphic(new ImageView(
                        "icons/com.iconfinder/tango-icon-library/1415555523_folder-20.png"));
            }
        }
    }
}
