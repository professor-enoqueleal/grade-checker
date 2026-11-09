import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Modal } from 'bootstrap';

/** Bootstrap modal bound to a template ref: show(), hide(), disposed with the component. */
export function useModal() {
  const element = ref<HTMLElement | null>(null);
  let modal: Modal | null = null;

  onMounted(() => {
    if (element.value) modal = Modal.getOrCreateInstance(element.value);
  });
  onBeforeUnmount(() => {
    modal?.dispose();
    modal = null;
    // a modal open while leaving the page leaves the backdrop and the body class behind
    document.querySelectorAll('.modal-backdrop').forEach((b) => b.remove());
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('padding-right');
  });

  return {
    element,
    show: () => modal?.show(),
    hide: () => modal?.hide(),
  };
}
