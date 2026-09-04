-- Backfill demo Wedding Guide task labels in ES/FR without overwriting edits.
INSERT INTO guide_task_translation (task_id, locale, label)
SELECT task.id, seed.locale, seed.label
FROM guide
JOIN guide_task task ON task.guide_id = guide.id
JOIN (
  SELECT 'Dream & Discover' guide_title, 0 sort_order, 'es' locale, 'Define el estilo y el tipo de lugar de tu boda' label UNION ALL
  SELECT 'Dream & Discover', 1, 'es', 'Establece el presupuesto para tu vestido' UNION ALL
  SELECT 'Dream & Discover', 2, 'es', 'Crea un moodboard' UNION ALL
  SELECT 'Dream & Discover', 3, 'es', 'Reserva una consulta de paleta de colores' UNION ALL
  SELECT 'Dream & Discover', 0, 'fr', 'Définissez l ambiance et le type de lieu de votre mariage' UNION ALL
  SELECT 'Dream & Discover', 1, 'fr', 'Fixez le budget de votre robe' UNION ALL
  SELECT 'Dream & Discover', 2, 'fr', 'Créez un moodboard' UNION ALL
  SELECT 'Dream & Discover', 3, 'fr', 'Réservez une consultation de palette de couleurs' UNION ALL
  SELECT 'Find Your Gown', 0, 'es', 'Explora siluetas según el lugar' UNION ALL
  SELECT 'Find Your Gown', 1, 'es', 'Pide muestras de tela' UNION ALL
  SELECT 'Find Your Gown', 2, 'es', 'Pruébate estilos en casa' UNION ALL
  SELECT 'Find Your Gown', 3, 'es', 'Haz el pedido de tu vestido con tiempo para personalizarlo' UNION ALL
  SELECT 'Find Your Gown', 0, 'fr', 'Explorez les silhouettes selon le lieu' UNION ALL
  SELECT 'Find Your Gown', 1, 'fr', 'Commandez des échantillons de tissu' UNION ALL
  SELECT 'Find Your Gown', 2, 'fr', 'Essayez les modèles chez vous' UNION ALL
  SELECT 'Find Your Gown', 3, 'fr', 'Commandez votre robe en prévoyant le délai de personnalisation' UNION ALL
  SELECT 'Style Your Party', 0, 'es', 'Elige la paleta de tus damas de honor' UNION ALL
  SELECT 'Style Your Party', 1, 'es', 'Comparte el enlace del grupo con tu cortejo' UNION ALL
  SELECT 'Style Your Party', 2, 'es', 'Pide el vestido de la madre de la novia' UNION ALL
  SELECT 'Style Your Party', 3, 'es', 'Elige los looks de las niñas de las flores' UNION ALL
  SELECT 'Style Your Party', 0, 'fr', 'Choisissez la palette de vos demoiselles d honneur' UNION ALL
  SELECT 'Style Your Party', 1, 'fr', 'Partagez le lien du groupe avec votre cortège' UNION ALL
  SELECT 'Style Your Party', 2, 'fr', 'Commandez la tenue de la mère de la mariée' UNION ALL
  SELECT 'Style Your Party', 3, 'fr', 'Choisissez les tenues des demoiselles d honneur junior' UNION ALL
  SELECT 'Accessorize', 0, 'es', 'Elige el velo y el tocado' UNION ALL
  SELECT 'Accessorize', 1, 'es', 'Elige los zapatos de boda' UNION ALL
  SELECT 'Accessorize', 2, 'es', 'Añade joyas y los toques finales' UNION ALL
  SELECT 'Accessorize', 3, 'es', 'Planea un segundo look para la recepción' UNION ALL
  SELECT 'Accessorize', 0, 'fr', 'Choisissez votre voile et votre coiffe' UNION ALL
  SELECT 'Accessorize', 1, 'fr', 'Choisissez vos chaussures de mariage' UNION ALL
  SELECT 'Accessorize', 2, 'fr', 'Ajoutez bijoux et touches finales' UNION ALL
  SELECT 'Accessorize', 3, 'fr', 'Prévoyez une seconde tenue pour la réception' UNION ALL
  SELECT 'Final Fittings', 0, 'es', 'Programa los arreglos' UNION ALL
  SELECT 'Final Fittings', 1, 'es', 'Estrena poco a poco tus zapatos' UNION ALL
  SELECT 'Final Fittings', 2, 'es', 'Haz la revisión final de los accesorios' UNION ALL
  SELECT 'Final Fittings', 3, 'es', 'Confirma las fechas de entrega' UNION ALL
  SELECT 'Final Fittings', 0, 'fr', 'Planifiez les retouches' UNION ALL
  SELECT 'Final Fittings', 1, 'fr', 'Assouplissez vos chaussures' UNION ALL
  SELECT 'Final Fittings', 2, 'fr', 'Vérifiez une dernière fois les accessoires' UNION ALL
  SELECT 'Final Fittings', 3, 'fr', 'Confirmez les dates de livraison' UNION ALL
  SELECT 'Wedding Week Ready', 0, 'es', 'Confirma las medidas finales y los arreglos' UNION ALL
  SELECT 'Wedding Week Ready', 1, 'es', 'Plancha con vapor y guarda el vestido con cuidado' UNION ALL
  SELECT 'Wedding Week Ready', 2, 'es', 'Prepara un kit de costura de emergencia' UNION ALL
  SELECT 'Wedding Week Ready', 3, 'es', 'Comparte el horario de preparación con tu cortejo' UNION ALL
  SELECT 'Wedding Week Ready', 4, 'es', 'Confirma el contacto de entrega del lugar' UNION ALL
  SELECT 'Wedding Week Ready', 0, 'fr', 'Confirmez les mesures finales et les retouches' UNION ALL
  SELECT 'Wedding Week Ready', 1, 'fr', 'Défroissez et rangez la robe avec soin' UNION ALL
  SELECT 'Wedding Week Ready', 2, 'fr', 'Préparez une trousse de couture d urgence' UNION ALL
  SELECT 'Wedding Week Ready', 3, 'fr', 'Partagez le planning des préparatifs avec votre cortège' UNION ALL
  SELECT 'Wedding Week Ready', 4, 'fr', 'Confirmez le contact de livraison du lieu' UNION ALL
  SELECT 'Preserve the Memories', 0, 'es', 'Organiza la limpieza profesional del vestido' UNION ALL
  SELECT 'Preserve the Memories', 1, 'es', 'Elige una caja de conservación o una vitrina' UNION ALL
  SELECT 'Preserve the Memories', 2, 'es', 'Guarda juntos el velo y los accesorios' UNION ALL
  SELECT 'Preserve the Memories', 3, 'es', 'Anota los proveedores y detalles que más te gustaron' UNION ALL
  SELECT 'Preserve the Memories', 0, 'fr', 'Organisez le nettoyage professionnel de la robe' UNION ALL
  SELECT 'Preserve the Memories', 1, 'fr', 'Choisissez une boîte de conservation ou une vitrine' UNION ALL
  SELECT 'Preserve the Memories', 2, 'fr', 'Rangez ensemble votre voile et vos accessoires' UNION ALL
  SELECT 'Preserve the Memories', 3, 'fr', 'Notez les prestataires et les détails que vous avez aimés'
) seed ON seed.guide_title = guide.title AND seed.sort_order = task.sort_order
WHERE NOT EXISTS (
  SELECT 1 FROM guide_task_translation existing
  WHERE existing.task_id = task.id AND existing.locale = seed.locale
);
