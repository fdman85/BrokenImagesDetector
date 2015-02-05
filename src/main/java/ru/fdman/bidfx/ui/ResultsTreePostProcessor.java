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
import ru.fdman.bidfx.process.processes.processor.result.ResultPostInfo;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Created by fdman on 07.10.2014.
 */
public class ResultsTreePostProcessor<T extends TreeItem<R>, R extends BytesProcessResult> {
    private final ExpandedPropertyListener expandedPropertyListener = new ExpandedPropertyListener(); //для смены иконки
    private final Logger log = LoggerFactory
            .getLogger(ResultsTreePostProcessor.class);
    private final T root;

    public ResultsTreePostProcessor(T root) {
        this.root = root;
    }

    public T cloneTree(T root) {
        //T newRoot = cloneTreeItem(root);
        return cloneTree(root, cloneTreeItem(root));
    }

    private T cloneTreeItem(T oldTreeItem) {
        T newTreeItem = (T) new TreeItem();
        newTreeItem.setValue(oldTreeItem.getValue());
        newTreeItem.getValue().setResultPostInfo(new ResultPostInfo());
        newTreeItem.setGraphic(oldTreeItem.getGraphic());
        newTreeItem.setExpanded(oldTreeItem.isExpanded());
        newTreeItem.expandedProperty().addListener(expandedPropertyListener);
        return newTreeItem;
    }

    /**
     * Итерироваться по дереву и отсортировать его с помощью sortTreeCallback (фильтруем детей текущего parentTreeItem в
     * Consumer sortTreeCallback), а также отфильтровать листья с помощью Predicate filterValuesCallback
     *
     * @param parentTreeItem
     * @param sortTreeCallback
     * @param filterValuesCallback
     */
    public void sortAndFilterTree(final T parentTreeItem, final Consumer<T> sortTreeCallback,
                                  Predicate<T> filterValuesCallback) {
        sortTreeCallback.accept(parentTreeItem); //остортируем "детей"
        ObservableList<T> children = (ObservableList<T>) parentTreeItem.getChildren();
        children.removeAll(children.filtered(filterValuesCallback)); //профильтруем все что нужно
        for (T childItem : children) {
            sortAndFilterTree(childItem, sortTreeCallback, filterValuesCallback);
        }
    }

    /**
     * Clone tree by starting from current root
     */
    private T cloneTree(final T oldParentTreeItem, T newParentTreeItem) {
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

    public void setFoldersInfo(final T parentTreeItem) {
        R parentItemValue = parentTreeItem.getValue();
        ObservableList<T> children = (ObservableList<T>) parentTreeItem.getChildren();
        //going deep into the tree
        for (T childItem : children) {
            R childItemValue = childItem.getValue();
            if (childItemValue != null) {
                if (childItemValue.isLeaf()) {
                    //all leafs are 1
                    childItemValue.getResultPostInfo().setTotalInside(1L);
                    //there are no leaf that is worstest than itself
                    childItemValue.getResultPostInfo().setWorstStatus(childItemValue.getStatus());
                    //
                    childItemValue.getResultPostInfo().getByStatusesMap().put(
                            childItemValue.getStatus(),
                            childItemValue.getResultPostInfo().getByStatusesMap().get(childItemValue.getStatus()) + 1L);
                }
            }
            //going deeper
            setFoldersInfo(childItem);
        }
        //going from deep to outside of tree
        //if (!parentItemValue.isLeaf()) {
            //yep, we know how many leafs are here
            parentItemValue.setDescription("Total: " + parentItemValue.getResultPostInfo().getTotalInside() +
                    " " + parentItemValue.getResultPostInfo().getWorstStatus().toString());
        parentItemValue.setDetails("" + parentItemValue.getResultPostInfo().getByStatusesMap());

        //}

        //pushing total info up and accumulate it there:
        TreeItem<R> parentOfParent = parentTreeItem.getParent();     //parentOfParent, yeah baby
        if (parentOfParent != null) {
            //yep, say about how many leafs are here to outside
            parentOfParent.getValue().getResultPostInfo().setTotalInside(parentOfParent.getValue().getResultPostInfo().getTotalInside() + parentItemValue.getResultPostInfo().getTotalInside());

            //push status statistics up
            //TODO fix by java 8 stream. concatenate http://docs.oracle.com/javase/tutorial/collections/streams/reduction.html
            parentOfParent.getValue().getResultPostInfo().getByStatusesMap().put(
                    parentItemValue.getStatus(),
                    parentItemValue.getResultPostInfo().getByStatusesMap().get(parentItemValue.getStatus()) + 1L);

            //push worstest status up
            if (parentOfParent.getValue().getResultPostInfo().getWorstStatus().getPriority() < parentItemValue.getResultPostInfo().getWorstStatus().getPriority()) {
                parentOfParent.getValue().getResultPostInfo().setWorstStatus(parentItemValue.getResultPostInfo().getWorstStatus());
            }
        }
    }

    private class ExpandedPropertyListener implements ChangeListener {
        @Override
        public void changed(ObservableValue observable, Object oldValue, Object newValue) {
            BooleanProperty booleanProperty = (BooleanProperty) observable;
            TreeItem t = (TreeItem) booleanProperty.getBean();
            if ((Boolean) newValue) {
                t.setGraphic(new ImageView(
                        "icons/com.iconfinder/tango-icon-library/1415555509_folder-open-20.png"));
            } else {
                t.setGraphic(new ImageView(
                        "icons/com.iconfinder/tango-icon-library/1415555523_folder-20.png"));
            }
        }
    }
}
