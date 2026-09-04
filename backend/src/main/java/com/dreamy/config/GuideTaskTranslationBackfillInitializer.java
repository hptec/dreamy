package com.dreamy.config;

import com.dreamy.domain.guide.entity.Guide;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.domain.guide.service.GuideTaskService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Backfills missing demo task translations without overwriting CMS-managed content. */
@Component
@Order(31)
public class GuideTaskTranslationBackfillInitializer {
    private final GuideRepository guideRepository;
    private final GuideTaskService guideTaskService;

    public GuideTaskTranslationBackfillInitializer(GuideRepository guideRepository, GuideTaskService guideTaskService) {
        this.guideRepository = guideRepository;
        this.guideTaskService = guideTaskService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        Map<String, List<String>> es = Map.of(
                "Phase 1", List.of("Define el estilo y el tipo de lugar de tu boda", "Establece el presupuesto para tu vestido", "Crea un moodboard", "Reserva una consulta de paleta de colores"),
                "Phase 2", List.of("Explora siluetas según el lugar", "Pide muestras de tela", "Pruébate estilos en casa", "Haz el pedido de tu vestido con tiempo para personalizarlo"),
                "Phase 3", List.of("Elige la paleta de tus damas de honor", "Comparte el enlace del grupo con tu cortejo", "Pide el vestido de la madre de la novia", "Elige los looks de las niñas de las flores"),
                "Phase 4", List.of("Elige el velo y el tocado", "Elige los zapatos de boda", "Añade joyas y los toques finales", "Planea un segundo look para la recepción"),
                "Phase 5", List.of("Programa los arreglos", "Estrena poco a poco tus zapatos", "Haz la revisión final de los accesorios", "Confirma las fechas de entrega"),
                "Phase 6", List.of("Confirma las medidas finales y los arreglos", "Plancha con vapor y guarda el vestido con cuidado", "Prepara un kit de costura de emergencia", "Comparte el horario de preparación con tu cortejo", "Confirma el contacto de entrega del lugar"),
                "Phase 7", List.of("Organiza la limpieza profesional del vestido", "Elige una caja de conservación o una vitrina", "Guarda juntos el velo y los accesorios", "Anota los proveedores y detalles que más te gustaron"));
        Map<String, List<String>> fr = Map.of(
                "Phase 1", List.of("Définissez l ambiance et le type de lieu de votre mariage", "Fixez le budget de votre robe", "Créez un moodboard", "Réservez une consultation de palette de couleurs"),
                "Phase 2", List.of("Explorez les silhouettes selon le lieu", "Commandez des échantillons de tissu", "Essayez les modèles chez vous", "Commandez votre robe en prévoyant le délai de personnalisation"),
                "Phase 3", List.of("Choisissez la palette de vos demoiselles d honneur", "Partagez le lien du groupe avec votre cortège", "Commandez la tenue de la mère de la mariée", "Choisissez les tenues des demoiselles d honneur junior"),
                "Phase 4", List.of("Choisissez votre voile et votre coiffe", "Choisissez vos chaussures de mariage", "Ajoutez bijoux et touches finales", "Prévoyez une seconde tenue pour la réception"),
                "Phase 5", List.of("Planifiez les retouches", "Assouplissez vos chaussures", "Vérifiez une dernière fois les accessoires", "Confirmez les dates de livraison"),
                "Phase 6", List.of("Confirmez les mesures finales et les retouches", "Défroissez et rangez la robe avec soin", "Préparez une trousse de couture d urgence", "Partagez le planning des préparatifs avec votre cortège", "Confirmez le contact de livraison du lieu"),
                "Phase 7", List.of("Organisez le nettoyage professionnel de la robe", "Choisissez une boîte de conservation ou une vitrine", "Rangez ensemble votre voile et vos accessoires", "Notez les prestataires et les détails que vous avez aimés"));
        for (Guide guide : guideRepository.listAdmin(null)) {
            List<String> spanish = es.get(guide.getPhase());
            List<String> french = fr.get(guide.getPhase());
            if (spanish != null) guideTaskService.seedMissingTranslations(guide.getId(), "es", spanish);
            if (french != null) guideTaskService.seedMissingTranslations(guide.getId(), "fr", french);
        }
    }
}
