/*
 * This file is part of Haveno.
 *
 * Haveno is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Haveno is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Haveno. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.desktop.main.support.dispute.agent;

import haveno.common.UserThread;
import haveno.common.util.Utilities;
import haveno.core.locale.Res;
import haveno.core.offer.OpenOfferManager;
import haveno.core.offer.SignedOffer;
import haveno.core.trade.HavenoUtils;
import haveno.core.xmr.wallet.XmrWalletService;
import haveno.desktop.common.view.ActivatableView;
import haveno.desktop.common.view.FxmlView;
import haveno.desktop.components.AutoTooltipLabel;
import haveno.desktop.components.AutoTooltipTableColumn;
import haveno.desktop.components.HyperlinkWithIcon;
import haveno.desktop.components.InputTextField;
import haveno.desktop.main.offer.OfferViewUtil;
import haveno.desktop.main.overlays.popups.Popup;
import haveno.desktop.util.DisplayUtils;
import haveno.desktop.util.GUIUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.Duration;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.Date;

@FxmlView
public class SignedOfferView extends ActivatableView<VBox, Void> {

    private final OpenOfferManager openOfferManager;

    @FXML
    protected TableView<SignedOffer> tableView;
    @FXML
    TableColumn<SignedOffer, SignedOffer> dateColumn;
    @FXML
    TableColumn<SignedOffer, SignedOffer> offerIdColumn;
    @FXML
    TableColumn<SignedOffer, SignedOffer> reserveTxHashColumn;
    @FXML
    TableColumn<SignedOffer, SignedOffer> reserveTxHexColumn;
    @FXML
    TableColumn<SignedOffer, SignedOffer> reserveTxKeyImages;
    @FXML
    TableColumn<SignedOffer, SignedOffer> arbitratorSignatureColumn;
    @FXML
    TableColumn<SignedOffer, SignedOffer> reserveTxMinerFeeColumn;
    @FXML
    TableColumn<SignedOffer, SignedOffer> makerPenaltyFeeColumn;
    @FXML
    InputTextField filterTextField;
    @FXML
    Label numItems;
    @FXML
    Region footerSpacer;

    private SignedOffer selectedSignedOffer;

    private XmrWalletService xmrWalletService;

    private ContextMenu contextMenu;

    private final ListChangeListener<SignedOffer> signedOfferListChangeListener;

    @Inject
    public SignedOfferView(OpenOfferManager openOfferManager, XmrWalletService xmrWalletService) {
        this.openOfferManager = openOfferManager;
        this.xmrWalletService = xmrWalletService;

        signedOfferListChangeListener = change -> applyList();
    }

    private void applyList() {
        UserThread.execute(() -> {
            SortedList<SignedOffer> sortedList = new SortedList<>(openOfferManager.getObservableSignedOffersList());
            sortedList.comparatorProperty().bind(tableView.comparatorProperty());
            tableView.setItems(sortedList);
            numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Life cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        Label label = new AutoTooltipLabel(Res.get("support.filter"));
        HBox.setMargin(label, new Insets(5, 0, 0, 0));
        HBox.setHgrow(label, Priority.NEVER);

        filterTextField = new InputTextField();
        Tooltip tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(100));
        tooltip.setShowDuration(Duration.seconds(10));
        filterTextField.setTooltip(tooltip);
        HBox.setHgrow(filterTextField, Priority.NEVER);

        filterTextField.setText("open");

        setupTable();
    }
    @Override
    protected void activate() {
        super.activate();

        applyList();
        openOfferManager.getObservableSignedOffersList().addListener(signedOfferListChangeListener);
        contextMenu = new ContextMenu();
        MenuItem item1 = new MenuItem(Res.get("support.contextmenu.penalize.msg",
                Res.get("shared.maker")));
        contextMenu.getItems().addAll(item1);

        tableView.setRowFactory(tv -> {
            TableRow<SignedOffer> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                contextMenu.show(row, event.getScreenX(), event.getScreenY());
            });
            return row;
        });

        item1.setOnAction(event -> {
            selectedSignedOffer = tableView.getSelectionModel().getSelectedItem();
            if(selectedSignedOffer != null) {
                new Popup().warning(Res.get("support.prompt.signedOffer.penalty.msg",
                        selectedSignedOffer.getOfferId(),
                        HavenoUtils.formatXmr(selectedSignedOffer.getPenaltyAmount(), true),
                        HavenoUtils.formatXmr(selectedSignedOffer.getReserveTxMinerFee(), true),
                        selectedSignedOffer.getReserveTxHash(),
                        selectedSignedOffer.getReserveTxKeyImages())
                ).onAction(() -> OfferViewUtil.submitTransactionHex(xmrWalletService, tableView,
                        selectedSignedOffer.getReserveTxHex())).show();
            } else {
                new Popup().error(Res.get("support.prompt.signedOffer.error.msg")).show();
            }
        });

        GUIUtil.requestFocus(tableView);
    }

    @Override
    protected void deactivate() {
        super.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SignedOfferView
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("support.noTickets"));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        tableView.getSelectionModel().clearSelection();

        dateColumn = getDateColumn();
        tableView.getColumns().add(dateColumn);

        offerIdColumn = getOfferIdColumn();
        tableView.getColumns().add(offerIdColumn);

        reserveTxHashColumn = getReserveTxHashColumn();
        tableView.getColumns().add(reserveTxHashColumn);

        reserveTxHexColumn = getReserveTxHexColumn();
        tableView.getColumns().add(reserveTxHexColumn);

        reserveTxKeyImages = getReserveTxKeyImagesColumn();
        tableView.getColumns().add(reserveTxKeyImages);

        arbitratorSignatureColumn = getArbitratorSignatureColumn();
        tableView.getColumns().add(arbitratorSignatureColumn);

        makerPenaltyFeeColumn = getMakerPenaltyFeeColumn();
        tableView.getColumns().add(makerPenaltyFeeColumn);

        reserveTxMinerFeeColumn = getReserveTxMinerFeeColumn();
        tableView.getColumns().add(reserveTxMinerFeeColumn);

        offerIdColumn.setComparator(Comparator.comparing(SignedOffer::getOfferId));
        dateColumn.setComparator(Comparator.comparing(SignedOffer::getTimeStamp));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    private TableColumn<SignedOffer, SignedOffer> getDateColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("shared.date")) {
            {
                setMinWidth(180);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(DisplayUtils.formatDateTime(new Date(item.getTimeStamp())));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getOfferIdColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("shared.offerId")) {
            {
                setMinWidth(110);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(item.getOfferId());
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    setText("");
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getReserveTxHashColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("support.txHash")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(item.getReserveTxHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getReserveTxHexColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("support.txHex")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(item.getReserveTxHex());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getReserveTxKeyImagesColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("support.txKeyImages")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(item.getReserveTxKeyImages().toString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getArbitratorSignatureColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("support.signature")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(Utilities.bytesAsHexString(item.getArbitratorSignature()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getMakerPenaltyFeeColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("support.maker.penalty.fee")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(HavenoUtils.formatXmr(item.getPenaltyAmount(), true));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SignedOffer, SignedOffer> getReserveTxMinerFeeColumn() {
        TableColumn<SignedOffer, SignedOffer> column = new AutoTooltipTableColumn<>(Res.get("support.tx.miner.fee")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((signedOffer) -> new ReadOnlyObjectWrapper<>(signedOffer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SignedOffer, SignedOffer> call(TableColumn<SignedOffer, SignedOffer> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SignedOffer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(HavenoUtils.formatXmr(item.getReserveTxMinerFee(), true));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }
}
