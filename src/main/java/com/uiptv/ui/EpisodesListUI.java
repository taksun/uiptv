package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.EpisodeList;
import com.uiptv.service.BookmarkService;
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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EpisodesListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    SearchableTableView<EpisodeItem> table = new SearchableTableView<>();
    TableColumn<EpisodeItem, String> channelName = new TableColumn("Episodes");
    private final EpisodeList channelList;

    public EpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI) {
        this.channelList = channelList;
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        this.categoryTitle = categoryTitle;
        initWidgets();
        refresh();
    }

    private void refresh() {
        List<EpisodeItem> catList = new ArrayList<>();
        channelList.episodes.forEach(i -> {
            Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getId(), i.getTitle(), i.getCmd(), account.getServerPortalUrl());
            boolean checkBookmark = BookmarkService.getInstance().isChannelBookmarked(b);
            UIptvAlert.showMessage(b + " --- " + String.valueOf(checkBookmark));
            catList.add(new EpisodeItem(new SimpleStringProperty(checkBookmark ? "**" + i.getTitle().replace("*", "") + "**" : i.getTitle()), new SimpleStringProperty(i.getContainerExtension()), new SimpleStringProperty(i.getId()), new SimpleStringProperty(i.getCmd())));
        });
        table.setItems(FXCollections.observableArrayList(catList));
        table.addTextFilter();
    }

    private void initWidgets() {
        setSpacing(10);
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getColumns().addAll(channelName);
        channelName.setText("Episodes of " + categoryTitle);
        channelName.setVisible(true);
        channelName.setCellValueFactory(cellData -> cellData.getValue().episodeNameProperty());
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
                download();
            }
        });
        table.setRowFactory(tv -> {
            TableRow<EpisodeItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    download();
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private void addRightClickContextMenu(TableRow<EpisodeItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);
        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            rowMenu.hide();
            play1(row);
        });
        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            rowMenu.hide();
            play2(row);
        });
        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            rowMenu.hide();
            play3(row);
        });
        MenuItem downloadItem = new MenuItem("Download");
        downloadItem.setOnAction(event -> {
            rowMenu.hide();
            download();
        });

        MenuItem reconnectAndPlayItem = new MenuItem("Reconnect & Play");
        reconnectAndPlayItem.setOnAction(event -> {
            rowMenu.hide();
            reconnectAndPlay(row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
        });
        rowMenu.getItems().addAll(player1Item, player2Item, player3Item, downloadItem, reconnectAndPlayItem);

        // only display context menu for non-empty rows:
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void reconnectAndPlay(TableRow<EpisodeItem> row, String playerPath) {
        try {
            Platform.executeCommand(playerPath, PlayerService.getInstance().runBookmark(account, row.getItem().getCmd()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void play(EpisodeItem item) {
        Platform.executeCommand(ConfigurationService.getInstance().read().getDefaultPlayerPath(), getEpisodeUrl(item));
    }

    private void play1(TableRow<EpisodeItem> row) {
        Platform.executeCommand(ConfigurationService.getInstance().read().getPlayerPath1(), getEpisodeUrl(row.getItem()));
    }

    private void play2(TableRow<EpisodeItem> row) {
        Platform.executeCommand(ConfigurationService.getInstance().read().getPlayerPath2(), getEpisodeUrl(row.getItem()));
    }

    private void play3(TableRow<EpisodeItem> row) {
        Platform.executeCommand(ConfigurationService.getInstance().read().getPlayerPath3(), getEpisodeUrl(row.getItem()));
    }

    private void download() {
        Map<String, String> filesToDownload = table.getSelectionModel().getSelectedItems().stream().sorted(Comparator.comparing(EpisodeItem::getEpisodeId)).collect(Collectors.toMap(this::getEpisodeUrl, this::getFilePath, (a, b) -> a, LinkedHashMap::new));
        FileDownloader.openDownloadWindow(filesToDownload);
    }

    private String getFilePath(EpisodeItem item) {
        return item.getEpisodeName() + "." + item.getContainerExtension();
    }

    private String getEpisodeUrl(EpisodeItem item) {
        try {
            return PlayerService.getInstance().get(account, item.getCmd());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class EpisodeItem {

        private final SimpleStringProperty episodeName;
        private final SimpleStringProperty containerExtension;
        private final SimpleStringProperty episodeId;
        private final SimpleStringProperty cmd;

        public EpisodeItem(SimpleStringProperty episodeName, SimpleStringProperty containerExtension, SimpleStringProperty episodeId, SimpleStringProperty cmd) {
            this.episodeName = episodeName;
            this.containerExtension = containerExtension;
            this.episodeId = episodeId;
            this.cmd = cmd;
        }

        public String getEpisodeName() {
            return episodeName.get();
        }

        public void setEpisodeName(String episodeName) {
            this.episodeName.set(episodeName);
        }

        public String getContainerExtension() {
            return containerExtension.get();
        }

        public SimpleStringProperty containerExtensionProperty() {
            return containerExtension;
        }

        public String getEpisodeId() {
            return episodeId.get();
        }

        public void setEpisodeId(String episodeId) {
            this.episodeId.set(episodeId);
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

        public SimpleStringProperty episodeNameProperty() {
            return episodeName;
        }

        public SimpleStringProperty episodeIdProperty() {
            return episodeId;
        }
    }
}