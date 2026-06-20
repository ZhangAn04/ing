package it.polimi.Network.Client;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * A custom JavaFX component representing a game card on the table.
 * Displays the card image, handles hover effects, and supports click events.
 */
public class CardNode extends StackPane {
    private static final double CARD_WIDTH = 92;
    private static final double CARD_HEIGHT = 138;
    private final int assetId;
    private final ImageView imageView;
    private final Rectangle placeholder;
    private final DropShadow glowEffect;
    
    /**
     * Creates a new CardNode.
     *
     * @param assetId The ID of the card image.
     * @param tooltipText Optional tooltip text describing the card.
     * @param onClick Runnable to execute when the card is clicked.
     */
    public CardNode(int assetId, String tooltipText, Runnable onClick) {
        this(assetId, tooltipText, onClick, CARD_WIDTH);
    }

    /**
     * Creates a new CardNode with a custom width.
     *
     * @param assetId The ID of the card image.
     * @param tooltipText Optional tooltip text describing the card.
     * @param onClick Runnable to execute when the card is clicked.
     * @param cardWidth The target width to scale the card to.
     */
    public CardNode(int assetId, String tooltipText, Runnable onClick, double cardWidth) {
        this.assetId = assetId;
        this.setAlignment(Pos.CENTER);
        double cardHeight = CARD_HEIGHT * (cardWidth / CARD_WIDTH);

        glowEffect = new DropShadow(20, Color.web("#f6e05e"));
        glowEffect.setSpread(0.6);

        Image img = ImageLoader.getCardImage(assetId);
        
        if (img != null) {
            imageView = new ImageView(img);
            double cropX = img.getWidth() * 0.08;
            double cropY = img.getHeight() * 0.055;
            double cropWidth = img.getWidth() * 0.84;
            double cropHeight = img.getHeight() * 0.89;
            imageView.setViewport(new Rectangle2D(cropX, cropY, cropWidth, cropHeight));
            imageView.setFitWidth(cardWidth);
            imageView.setPreserveRatio(true);
            
            // Round the corners of the image
            Rectangle clip = new Rectangle(cardWidth, cardHeight); // Approximate height, will adjust based on ratio
            clip.setArcWidth(15);
            clip.setArcHeight(15);
            // We use a slight hack to clip bounds since image height varies slightly
            // imageView.setClip(clip); 
            
            // Base shadow for all cards to look like they are on a table
            this.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 10, 0, 3, 5); -fx-background-radius: 10;");

            placeholder = null;
            this.getChildren().add(imageView);
        } else {
            imageView = null;
            placeholder = new Rectangle(cardWidth, cardHeight, Color.web("#2d3748"));
            placeholder.setArcWidth(15);
            placeholder.setArcHeight(15);
            placeholder.setStroke(Color.web("#4a5568"));
            placeholder.setStrokeWidth(2);
            
            // Base shadow
            this.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 10, 0, 3, 5);");
            
            this.getChildren().add(placeholder);
        }

        if (tooltipText != null && !tooltipText.trim().isEmpty()) {
            Tooltip t = new Tooltip(tooltipText);
            t.setStyle("-fx-background-color: rgba(20,25,30,0.9); -fx-text-fill: white; -fx-font-size: 14px; -fx-border-color: #4a5568; -fx-border-width: 2; -fx-border-radius: 5;");
            Tooltip.install(this, t);
        }

        this.setOnMouseEntered(e -> {
            this.setEffect(glowEffect);
            this.setScaleX(1.05);
            this.setScaleY(1.05);
        });

        this.setOnMouseExited(e -> {
            this.setEffect(null);
            this.setScaleX(1.0);
            this.setScaleY(1.0);
        });

        if (onClick != null) {
            this.setOnMouseClicked(e -> onClick.run());
            this.setCursor(javafx.scene.Cursor.HAND);
        }
    }

    /**
     * Returns the identifier of the card image represented by this node.
     *
     * @return The asset ID of this card.
     */
    public int getAssetId() {
        return assetId;
    }
}
