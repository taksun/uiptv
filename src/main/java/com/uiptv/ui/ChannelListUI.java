package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.FileDownloader;
import com.uiptv.util.Platform;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.UIptvAlert;
import com.uiptv.widget.SearchableTableView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.StringUtils.isBlank;

public class ChannelListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    private final String categoryId;
    SearchableTableView table = new SearchableTableView();
    TableColumn<ChannelItem, String> channelName = new TableColumn("Channels");
    private final List<Channel> channelList;


    public ChannelListUI(List<Channel> channelList, Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI, String categoryId) {
        this.categoryId = categoryId;
        this.channelList = channelList;
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        this.categoryTitle = categoryTitle;
        initWidgets();
        refresh();

    }

    private void refresh() {
        List<ChannelItem> catList = new ArrayList<>();
        channelList.forEach(i -> {
            Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getChannelId(), i.getName(), i.getCmd(), account.getServerPortalUrl());
            boolean checkBookmark = BookmarkService.getInstance().isChannelBookmarked(b);
            UIptvAlert.showMessage(b + " --- " + checkBookmark);
            catList.add(new ChannelItem(new SimpleStringProperty(checkBookmark ? "**" + i.getName().replace("*", "") + "**" : i.getName()), new SimpleStringProperty(i.getContainerExtension()), new SimpleStringProperty(i.getChannelId()), new SimpleStringProperty(i.getCmd())));
        });
        table.setItems(FXCollections.observableArrayList(catList));
        table.addTextFilter();
    }

    private void initWidgets() {
        setSpacing(10);
        table.setEditable(true);
        table.getColumns().addAll(channelName);
        channelName.setText(categoryTitle);
        channelName.setVisible(true);
        channelName.setCellValueFactory(cellData -> cellData.getValue().channelNameProperty());
        channelName.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                if (item != null && !empty) {
                    if (item.startsWith("**")) {
                        setStyle("-fx-font-weight: bold;-fx-font-size: 125%;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        channelName.setSortType(TableColumn.SortType.ASCENDING);
        getChildren().addAll(new AutoGrowVBox(5, table.getSearchTextField(), table));
        addChannelClickHandler();
    }

    private void addChannelClickHandler() {
        table.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                downloadOrShowSeries((ChannelItem) table.getFocusModel().getFocusedItem());
            }
        });
        table.setRowFactory(tv -> {
            TableRow<ChannelItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    downloadOrShowSeries(row.getItem());
                }
            });

            addRightClickContextMenu(row);

            return row;
        });
    }

    private void downloadOrShowSeries(ChannelItem item) {
        if (account.getAction() == series && account.getType() == XTREME_API) {
            if (this.getChildren().size() > 1) {
                this.getChildren().remove(1);
            }
            this.getChildren().add(new EpisodesListUI(XtremeParser.parseEpisodes(item.getChannelId(), account), account, item.getChannelName(), bookmarkChannelListUI));
        } else if (account.getAction() == series && account.getType() == STALKER_PORTAL) {
            if (isBlank(item.getCmd())) {
                if (this.getChildren().size() > 1) {
                    this.getChildren().remove(1);
                }
                this.getChildren().clear();
                getChildren().addAll(new VBox(5, table.getSearchTextField(), table), new ChannelListUI(ChannelService.getInstance().getSeries(categoryId, item.getChannelId(), account), account, item.getChannelName(), bookmarkChannelListUI, categoryId));
            } else {
                download(item);
            }
        } else {
            download(item);
        }
    }

    private void addRightClickContextMenu(TableRow<ChannelItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);
        MenuItem editItem = new MenuItem("Toggle Bookmark");
        editItem.setOnAction(actionEvent -> {
            rowMenu.hide();
            BookmarkService.getInstance().toggleBookmark(new Bookmark(account.getAccountName(), categoryTitle, row.getItem().getChannelId(), row.getItem().getChannelName(), row.getItem().getCmd(), account.getServerPortalUrl()));
            this.refresh();
            bookmarkChannelListUI.refresh();
        });
        if (!(account.getAction() == series && account.getType() == XTREME_API)) {
            MenuItem player1Item = new MenuItem("Player 1");
            player1Item.setOnAction(event -> {
                rowMenu.hide();
                play1(row.getItem());
            });
            MenuItem player2Item = new MenuItem("Player 2");
            player2Item.setOnAction(event -> {
                rowMenu.hide();
                play2(row.getItem());
            });
            MenuItem player3Item = new MenuItem("Player 3");
            player3Item.setOnAction(event -> {
                rowMenu.hide();
                play3(row.getItem());
            });
            MenuItem downloadItem = new MenuItem("Download");
            downloadItem.setOnAction(event -> {
                rowMenu.hide();
                download(row.getItem());
            });

            MenuItem reconnectAndPlayItem = new MenuItem("Reconnect & Play");
            reconnectAndPlayItem.setOnAction(event -> {
                rowMenu.hide();
                reconnectAndPlay(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
            });
            rowMenu.getItems().addAll(editItem, player1Item, player2Item, player3Item, downloadItem, reconnectAndPlayItem);
        } else {
            rowMenu.getItems().addAll(editItem);
        }

        // only display context menu for non-empty rows:
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void reconnectAndPlay(ChannelItem item, String playerPath) {
        try {
            Platform.executeCommand(playerPath, PlayerService.getInstance().runBookmark(account, item.getCmd()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void play(ChannelItem item) {
        try {
            Platform.executeCommand(ConfigurationService.getInstance().read().getDefaultPlayerPath(), PlayerService.getInstance().get(account, item.getCmd(), item.getChannelId()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void play1(ChannelItem item) {
        try {
            Platform.executeCommand(ConfigurationService.getInstance().read().getPlayerPath1(), PlayerService.getInstance().get(account, item.getCmd(), item.getChannelId()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void play2(ChannelItem item) {
        try {
            Platform.executeCommand(ConfigurationService.getInstance().read().getPlayerPath2(), PlayerService.getInstance().get(account, item.getCmd(), item.getChannelId()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void play3(ChannelItem item) {
        try {
            Platform.executeCommand(ConfigurationService.getInstance().read().getPlayerPath3(), PlayerService.getInstance().get(account, item.getCmd(), item.getChannelId()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void download(ChannelItem item) {
        try {
            Map<String, String> filesToDownload = Map.of(PlayerService.getInstance().get(account, item.getCmd()), item.getChannelName() + "." + item.getContainerExtension());
            FileDownloader.openDownloadWindow(filesToDownload);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ChannelItem {

        private final SimpleStringProperty channelName;
        private final SimpleStringProperty containerExtension;
        private final SimpleStringProperty channelId;
        private final SimpleStringProperty cmd;

        public ChannelItem(SimpleStringProperty channelName, SimpleStringProperty containerExtension, SimpleStringProperty channelId, SimpleStringProperty cmd) {
            this.channelName = channelName;
            this.containerExtension = containerExtension;
            this.channelId = channelId;
            this.cmd = cmd;
        }

        public String getChannelName() {
            return channelName.get();
        }

        public void setChannelName(String channelName) {
            this.channelName.set(channelName);
        }

        public String getContainerExtension() {
            return containerExtension.get();
        }

        public SimpleStringProperty containerExtensionProperty() {
            return containerExtension;
        }

        public String getChannelId() {
            return channelId.get();
        }

        public void setChannelId(String channelId) {
            this.channelId.set(channelId);
        }

        public String getCmd() {
            return cmd.get();
        }

        public void setCmd(String cmd) {
            this.cmd.set(cmd);
        }

        public SimpleStringProperty cmdProperty() {
            return cmd;
        }

        public SimpleStringProperty channelNameProperty() {
            return channelName;
        }

        public SimpleStringProperty channelIdProperty() {
            return channelId;
        }
    }
}